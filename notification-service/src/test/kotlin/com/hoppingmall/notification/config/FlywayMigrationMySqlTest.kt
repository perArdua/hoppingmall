package com.hoppingmall.notification.config

import com.hoppingmall.notification.support.MySqlContainerSupport
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

@DisplayName("notification 스키마 이력(Flyway)과 엔티티 검증")
@DisplayNameGeneration(ReplaceUnderscores::class)
class FlywayMigrationMySqlTest {

    @ParameterizedTest
    @ValueSource(strings = ["docker", "k8s"])
    fun 빈_DB에서는_마이그레이션으로_스키마를_만들고_validate로_기동한다(profile: String) {
        val database = freshDatabase("empty_$profile")

        runner(database, profile).run { context ->
            assertThat(context).hasNotFailed()
        }

        assertThat(history(database)).containsExactly("1:SQL:true", "2:SQL:true")
    }

    @Test
    fun ddl_auto가_만든_기존_DB에_붙이면_기준선으로_표시하고_데이터를_그대로_둔다() {
        val database = freshDatabase("existing")
        legacyUpdateRunner(database).run { context -> assertThat(context).hasNotFailed() }
        jdbc(database).update(
            "INSERT INTO notifications (user_id, type, title, event_id, created_at) VALUES (7, 'POINT_EARNED', '적립', 'evt-existing', NOW(6))"
        )

        runner(database, "docker").run { context ->
            assertThat(context).hasNotFailed()
        }

        assertThat(history(database)).containsExactly("1:BASELINE:true", "2:SQL:true")
        assertThat(jdbc(database).queryForList("SELECT event_id FROM notifications", String::class.java)).containsExactly("evt-existing")
    }

    @Test
    fun 옛_코드가_만든_ENUM_열은_값을_그대로_둔_채_VARCHAR로_바꾸고_기동한다() {
        val database = freshDatabase("legacy_enum")
        legacyUpdateRunner(database).run { context -> assertThat(context).hasNotFailed() }
        jdbc(database).execute(
            "ALTER TABLE notifications MODIFY type enum('PAYMENT_COMPLETED','PAYMENT_FAILED','PAYMENT_CANCELLED'," +
                "'REFUND_COMPLETED','POINT_EARNED','SHIPPING_STARTED','SHIPPING_DELIVERED') NOT NULL"
        )
        jdbc(database).execute("ALTER TABLE dlq_messages MODIFY status enum('PENDING','RETRYING','PROCESSED','FAILED') NOT NULL")
        jdbc(database).update(
            "INSERT INTO notifications (user_id, type, title, event_id, created_at) VALUES (7, 'SHIPPING_STARTED', '배송', 'evt-legacy', NOW(6))"
        )

        runner(database, "docker").run { context ->
            assertThat(context).hasNotFailed()
        }

        assertThat(history(database)).containsExactly("1:BASELINE:true", "2:SQL:true")
        assertThat(columnType(database, "notifications", "type")).isEqualTo("varchar(32)")
        assertThat(columnType(database, "dlq_messages", "status")).isEqualTo("varchar(32)")
        assertThat(jdbc(database).queryForList("SELECT type FROM notifications", String::class.java)).containsExactly("SHIPPING_STARTED")
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
        jdbc(database).execute("ALTER TABLE notifications DROP COLUMN metadata")

        runner(database, "docker").run { context ->
            assertThat(context).hasFailed()
            assertThat(context.startupFailure).rootCause().isInstanceOf(SchemaManagementException::class.java)
            assertThat(context.startupFailure).rootCause().hasMessageContaining("metadata")
        }
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
        val database = "notification_flyway_${label}_${SEQUENCE.incrementAndGet()}"
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
