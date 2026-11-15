package com.hoppingmall.settlement.config

import com.hoppingmall.settlement.support.MySqlContainerSupport
import org.assertj.core.api.Assertions.assertThat
import org.hibernate.tool.schema.spi.SchemaManagementException
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration
import org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import java.util.concurrent.atomic.AtomicInteger

@DisplayName("settlement 스키마 이력(Flyway)과 엔티티 검증")
@DisplayNameGeneration(ReplaceUnderscores::class)
class FlywayMigrationMySqlTest {

    @ParameterizedTest
    @ValueSource(strings = ["docker", "k8s"])
    fun 빈_DB에서는_마이그레이션으로_스키마를_만들고_validate로_기동한다(profile: String) {
        val database = freshDatabase("empty_$profile")

        runner(database, profile).run { context ->
            assertThat(context).hasNotFailed()
        }

        assertThat(history(database)).containsExactly("1:SQL:true", "2:SQL:true", "3:SQL:true")
    }

    @Test
    fun ddl_auto가_만든_기존_DB에_붙이면_기준선으로_표시하고_데이터를_그대로_둔다() {
        val database = freshDatabase("existing")
        legacyUpdateRunner(database).run { context -> assertThat(context).hasNotFailed() }
        dropObjectsAddedAfterBaseline(database)
        jdbc(database).update("INSERT INTO settlement_seller_locks (seller_id) VALUES (7)")

        runner(database, "docker").run { context ->
            assertThat(context).hasNotFailed()
        }

        assertThat(history(database)).containsExactly("1:BASELINE:true", "2:SQL:true", "3:SQL:true")
        assertThat(jdbc(database).queryForList("SELECT seller_id FROM settlement_seller_locks", Long::class.java)).containsExactly(7L)
    }

    @Test
    fun 옛_코드가_만든_ENUM_상태_열은_값을_그대로_둔_채_VARCHAR로_바꾸고_기동한다() {
        val database = freshDatabase("legacy_enum")
        legacyUpdateRunner(database).run { context -> assertThat(context).hasNotFailed() }
        dropObjectsAddedAfterBaseline(database)
        jdbc(database).execute("ALTER TABLE settlements MODIFY status enum('CALCULATED','CONFIRMED','PAID') NOT NULL")
        jdbc(database).execute("ALTER TABLE settlement_summaries MODIFY status enum('CALCULATED','CONFIRMED','PAID') NOT NULL")
        jdbc(database).update(
            "INSERT INTO settlements (seller_id, period_start, period_end, total_sales_amount, total_refund_amount, " +
                "commission_rate, commission_amount, settlement_amount, status, created_at) " +
                "VALUES (3, '2026-10-01', '2026-10-31', 100.00, 0.00, 0.1000, 10.00, 90.00, 'PAID', NOW(6))"
        )

        runner(database, "docker").run { context ->
            assertThat(context).hasNotFailed()
        }

        assertThat(history(database)).containsExactly("1:BASELINE:true", "2:SQL:true", "3:SQL:true")
        assertThat(columnType(database, "settlements", "status")).isEqualTo("varchar(32)")
        assertThat(columnType(database, "settlement_summaries", "status")).isEqualTo("varchar(32)")
        assertThat(jdbc(database).queryForList("SELECT status FROM settlements", String::class.java)).containsExactly("PAID")
    }

    @Test
    fun 마이그레이션이_만든_스키마가_엔티티가_만든_스키마와_같다() {
        val fromEntities = freshDatabase("entities")
        val fromMigrations = freshDatabase("migrations")
        entityCreateRunner(fromEntities).run { context -> assertThat(context).hasNotFailed() }
        runner(fromMigrations, "docker").run { context -> assertThat(context).hasNotFailed() }

        assertThat(tables(fromMigrations)).isEqualTo(tables(fromEntities))
        assertThat(columns(fromMigrations)).isEqualTo(columns(fromEntities))
        assertThat(indexes(fromMigrations)).isEqualTo(indexes(fromEntities))
        assertThat(foreignKeys(fromMigrations)).isEqualTo(foreignKeys(fromEntities))
        assertThat(checkConstraints(fromMigrations)).isEqualTo(checkConstraints(fromEntities))
    }

    @Test
    fun 엔티티의_열이_스키마에_없으면_validate가_기동을_막는다() {
        val database = freshDatabase("drift")
        runner(database, "docker").run { context -> assertThat(context).hasNotFailed() }
        jdbc(database).execute("ALTER TABLE settlements DROP COLUMN paid_at")

        runner(database, "docker").run { context ->
            assertThat(context).hasFailed()
            assertThat(context.startupFailure).rootCause().isInstanceOf(SchemaManagementException::class.java)
            assertThat(context.startupFailure).rootCause().hasMessageContaining("paid_at")
        }
    }

    @Test
    fun V3는_지급되지_않은_음수_정산을_지급액_0과_이월로_바꾸고_지급된_정산은_그대로_둔다() {
        val database = freshDatabase("negative")
        runner(database, "docker")
            .withPropertyValues("spring.flyway.target=2", "spring.jpa.hibernate.ddl-auto=none")
            .run { context -> assertThat(context).hasNotFailed() }
        val calculated = insertSettlement(database, sellerId = 5, month = 1, amount = "-20500.00", status = "CALCULATED")
        val paid = insertSettlement(database, sellerId = 5, month = 2, amount = "-100.00", status = "PAID")
        val positive = insertSettlement(database, sellerId = 5, month = 3, amount = "95000.00", status = "CONFIRMED")
        jdbc(database).update(
            "INSERT INTO settlement_summaries (settlement_id, seller_id, period_start, period_end, total_sales_amount, " +
                "total_refund_amount, commission_rate, commission_amount, settlement_amount, status, created_at) " +
                "VALUES (?, 5, '2026-01-01', '2026-01-31', 10000.00, 30000.00, 0.0500, 500.00, -20500.00, 'CALCULATED', NOW(6))",
            calculated
        )

        runner(database, "docker").run { context -> assertThat(context).hasNotFailed() }

        assertThat(history(database)).containsExactly("1:SQL:true", "2:SQL:true", "3:SQL:true")
        assertThat(amounts(database, "settlements", "id")).containsExactly(
            "$calculated|0.00|20500.00|0.00",
            "$paid|-100.00|0.00|0.00",
            "$positive|95000.00|0.00|0.00"
        )
        assertThat(amounts(database, "settlement_summaries", "settlement_id")).containsExactly("$calculated|0.00|20500.00|0.00")
        assertThat(
            jdbc(database).queryForList(
                "SELECT CONCAT_WS('|', seller_id, source_settlement_id, amount, IFNULL(applied_settlement_id, 'NULL')) FROM settlement_carry_overs",
                String::class.java
            )
        ).containsExactly("5|$calculated|20500.00|NULL")
    }

    private fun insertSettlement(database: String, sellerId: Long, month: Int, amount: String, status: String): Long {
        jdbc(database).update(
            "INSERT INTO settlements (seller_id, period_start, period_end, total_sales_amount, total_refund_amount, " +
                "commission_rate, commission_amount, settlement_amount, status, created_at) " +
                "VALUES (?, ?, ?, 10000.00, 30000.00, 0.0500, 500.00, ?, ?, NOW(6))",
            sellerId,
            "2026-%02d-01".format(month),
            "2026-%02d-28".format(month),
            amount,
            status
        )
        return jdbc(database).queryForObject("SELECT MAX(id) FROM settlements", Long::class.java)!!
    }

    private fun amounts(database: String, table: String, idColumn: String): List<String> =
        jdbc(database).queryForList(
            "SELECT CONCAT_WS('|', $idColumn, settlement_amount, carry_over_amount, carried_in_amount) FROM $table ORDER BY $idColumn",
            String::class.java
        )

    private fun dropObjectsAddedAfterBaseline(database: String) {
        jdbc(database).execute("DROP TABLE settlement_refunds")
        jdbc(database).execute("DROP TABLE settlement_carry_overs")
        jdbc(database).execute("ALTER TABLE settlements DROP COLUMN carried_in_amount, DROP COLUMN carry_over_amount")
        jdbc(database).execute("ALTER TABLE settlement_summaries DROP COLUMN carried_in_amount, DROP COLUMN carry_over_amount")
    }

    private fun runner(database: String, profile: String): ApplicationContextRunner =
        baseRunner(database)
            .withInitializer(ConfigDataApplicationContextInitializer())
            .withPropertyValues("spring.profiles.active=$profile")

    private fun legacyUpdateRunner(database: String): ApplicationContextRunner =
        baseRunner(database).withPropertyValues("spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=update")

    private fun entityCreateRunner(database: String): ApplicationContextRunner =
        baseRunner(database).withPropertyValues("spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create")

    private fun baseRunner(database: String): ApplicationContextRunner =
        ApplicationContextRunner()
            .withConfiguration(
                AutoConfigurations.of(
                    DataSourceAutoConfiguration::class.java,
                    FlywayAutoConfiguration::class.java,
                    HibernateJpaAutoConfiguration::class.java,
                    TransactionAutoConfiguration::class.java
                )
            )
            .withUserConfiguration(JpaConfig::class.java)
            .withPropertyValues(
                "spring.datasource.url=${MySqlContainerSupport.jdbcUrl(database)}",
                "spring.datasource.username=${MySqlContainerSupport.username}",
                "spring.datasource.password=${MySqlContainerSupport.password}",
                "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
                "spring.jpa.database-platform=org.hibernate.dialect.MySQLDialect"
            )

    private fun freshDatabase(label: String): String {
        val database = "settlement_flyway_${label}_${SEQUENCE.incrementAndGet()}"
        jdbc(database).execute("SELECT 1")
        return database
    }

    private fun jdbc(database: String): JdbcTemplate =
        JdbcTemplate(DriverManagerDataSource(MySqlContainerSupport.jdbcUrl(database), MySqlContainerSupport.username, MySqlContainerSupport.password))

    private fun history(database: String): List<String> =
        jdbc(database).queryForList(
            "SELECT CONCAT(version, ':', type, ':', IF(success, 'true', 'false')) FROM flyway_schema_history ORDER BY installed_rank",
            String::class.java
        )

    private fun columnType(database: String, table: String, column: String): String? =
        jdbc(database).queryForObject(
            "SELECT COLUMN_TYPE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? AND COLUMN_NAME = ?",
            String::class.java,
            database,
            table,
            column
        )

    private fun tables(database: String): List<String> =
        jdbc(database).queryForList(
            "SELECT CONCAT_WS('|', TABLE_NAME, ENGINE, TABLE_COLLATION) FROM information_schema.TABLES " +
                "WHERE TABLE_SCHEMA = ? AND TABLE_NAME <> 'flyway_schema_history' ORDER BY TABLE_NAME",
            String::class.java,
            database
        )

    private fun columns(database: String): List<String> =
        jdbc(database).queryForList(
            "SELECT CONCAT_WS('|', TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, IFNULL(COLUMN_DEFAULT, 'NULL'), EXTRA) " +
                "FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = ? AND TABLE_NAME <> 'flyway_schema_history' " +
                "ORDER BY TABLE_NAME, COLUMN_NAME",
            String::class.java,
            database
        )

    private fun indexes(database: String): List<String> =
        jdbc(database).queryForList(
            "SELECT CONCAT_WS('|', TABLE_NAME, NON_UNIQUE, GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX)) " +
                "FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = ? AND TABLE_NAME <> 'flyway_schema_history' " +
                "GROUP BY TABLE_NAME, INDEX_NAME, NON_UNIQUE",
            String::class.java,
            database
        ).sorted()

    private fun foreignKeys(database: String): List<String> =
        jdbc(database).queryForList(
            "SELECT CONCAT_WS('|', TABLE_NAME, COLUMN_NAME, REFERENCED_TABLE_NAME, REFERENCED_COLUMN_NAME) " +
                "FROM information_schema.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA = ? AND REFERENCED_TABLE_NAME IS NOT NULL",
            String::class.java,
            database
        ).sorted()

    private fun checkConstraints(database: String): List<String> =
        jdbc(database).queryForList(
            "SELECT CONCAT_WS('|', t.TABLE_NAME, c.CHECK_CLAUSE) FROM information_schema.TABLE_CONSTRAINTS t " +
                "JOIN information_schema.CHECK_CONSTRAINTS c ON c.CONSTRAINT_SCHEMA = t.CONSTRAINT_SCHEMA AND c.CONSTRAINT_NAME = t.CONSTRAINT_NAME " +
                "WHERE t.TABLE_SCHEMA = ? AND t.CONSTRAINT_TYPE = 'CHECK'",
            String::class.java,
            database
        ).sorted()

    companion object {
        private val SEQUENCE = AtomicInteger()
    }
}
