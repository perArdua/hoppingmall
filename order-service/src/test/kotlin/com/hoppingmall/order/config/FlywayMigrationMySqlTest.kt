package com.hoppingmall.order.config

import com.hoppingmall.dlq.domain.DLQStatus
import com.hoppingmall.order.order.enum.OrderStatus
import com.hoppingmall.order.refund.enum.RefundReason
import com.hoppingmall.order.refund.enum.RefundStatus
import com.hoppingmall.order.shipping.enum.ShippingStatus
import com.hoppingmall.order.support.MySqlContainerSupport
import com.hoppingmall.outbox.domain.OutboxStatus
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

@DisplayName("order 스키마 이력(Flyway)과 엔티티 검증")
@DisplayNameGeneration(ReplaceUnderscores::class)
class FlywayMigrationMySqlTest {

    @ParameterizedTest
    @ValueSource(strings = ["docker", "k8s"])
    fun 빈_DB에서는_마이그레이션으로_스키마를_만들고_validate로_기동한다(profile: String) {
        val database = freshDatabase("empty_$profile")

        runner(database, profile).run { context ->
            assertThat(context).hasNotFailed()
        }

        assertThat(history(database)).containsExactly("1:SQL:true", "2:SQL:true", "3:SQL:true", "4:SQL:true", "5:SQL:true")
    }

    @Test
    fun 서버_기본_정렬_규칙으로_만든_DB에서도_표와_문자열_열은_utf8mb4_unicode_ci다() {
        val database = "order_flyway_server_default_${SEQUENCE.incrementAndGet()}"
        jdbc(database).execute("SELECT 1")
        assertThat(databaseCollation(database)).isNotEqualTo(COLLATION)

        runner(database, "docker").run { context ->
            assertThat(context).hasNotFailed()
        }

        assertThat(tableCollations(database)).containsOnly(COLLATION)
        assertThat(columnCollations(database)).containsOnly(COLLATION)
    }

    @Test
    fun ddl_auto가_만든_기존_DB에_붙이면_기준선으로_표시하고_데이터를_그대로_둔다() {
        val database = freshDatabase("existing")
        legacyUpdateRunner(database).run { context -> assertThat(context).hasNotFailed() }
        insertDlqMessage(database, "PENDING")

        runner(database, "docker").run { context ->
            assertThat(context).hasNotFailed()
        }

        assertThat(history(database)).containsExactly("1:BASELINE:true", "2:SQL:true", "3:SQL:true", "4:SQL:true", "5:SQL:true")
        assertThat(jdbc(database).queryForList("SELECT status FROM dlq_messages", String::class.java)).containsExactly("PENDING")
    }

    @Test
    fun 옛_코드가_만든_ENUM_열은_값을_그대로_둔_채_VARCHAR로_바꾸고_기동한다() {
        val database = freshDatabase("legacy_enum")
        legacyUpdateRunner(database).run { context -> assertThat(context).hasNotFailed() }
        LEGACY_ENUM_COLUMNS.forEach { it.revertToEnum(jdbc(database)) }
        insertDlqMessage(database, "FAILED")

        runner(database, "docker").run { context ->
            assertThat(context).hasNotFailed()
        }

        assertThat(history(database)).containsExactly("1:BASELINE:true", "2:SQL:true", "3:SQL:true", "4:SQL:true", "5:SQL:true")
        LEGACY_ENUM_COLUMNS.forEach {
            assertThat(columnType(database, it.table, it.column)).isEqualTo("varchar(32)")
            assertThat(columnNullable(database, it.table, it.column)).isEqualTo(if (it.nullable) "YES" else "NO")
        }
        assertThat(jdbc(database).queryForList("SELECT status FROM dlq_messages", String::class.java)).containsExactly("FAILED")
    }

    @Test
    fun 기준선_뒤에_더해진_열이_없는_기존_DB에는_열을_더하고_기존_행은_제한_없음과_포인트_0이_된다() {
        val database = freshDatabase("before_v4")
        val fromEntities = freshDatabase("entities_v4")
        legacyUpdateRunner(database).run { context -> assertThat(context).hasNotFailed() }
        entityCreateRunner(fromEntities).run { context -> assertThat(context).hasNotFailed() }
        jdbc(database).execute("ALTER TABLE refunds DROP COLUMN point_refund_amount")
        jdbc(database).execute("ALTER TABLE order_items DROP COLUMN withdrawal_restricted, DROP COLUMN withdrawal_restriction_reason")
        jdbc(database).update(
            "INSERT INTO refunds (order_id, payment_id, buyer_id, seller_id, status, reason, refund_amount, is_full_refund, " +
                "return_shipping_fee, created_at) VALUES (1, 1, 9, 2, 'COMPLETED', 'CHANGE_OF_MIND', 10000.00, true, 0.00, NOW(6))"
        )
        jdbc(database).update(
            "INSERT INTO order_items (order_id, product_id, seller_id, product_name, product_price, quantity, total_price, created_at) " +
                "VALUES (1, 1, 2, 'item', 10000.00, 1, 10000.00, NOW(6))"
        )

        runner(database, "docker").run { context ->
            assertThat(context).hasNotFailed()
        }

        assertThat(history(database)).containsExactly("1:BASELINE:true", "2:SQL:true", "3:SQL:true", "4:SQL:true", "5:SQL:true")
        assertThat(columns(database)).isEqualTo(columns(fromEntities))
        assertThat(jdbc(database).queryForList("SELECT CAST(point_refund_amount AS CHAR) FROM refunds", String::class.java))
            .containsExactly("0.00")
        assertThat(
            jdbc(database).queryForList(
                "SELECT CONCAT_WS('|', withdrawal_restricted + 0, IFNULL(withdrawal_restriction_reason, 'NULL')) FROM order_items",
                String::class.java
            )
        ).containsExactly("0|NULL")
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
        jdbc(database).execute("ALTER TABLE orders DROP COLUMN delivered_at")

        runner(database, "docker").run { context ->
            assertThat(context).hasFailed()
            assertThat(context.startupFailure).rootCause().isInstanceOf(SchemaManagementException::class.java)
            assertThat(context.startupFailure).rootCause().hasMessageContaining("delivered_at")
        }
    }

    @Test
    fun V3는_배송_완료_시각이_없는_주문을_배송_행의_완료_시각으로_채운다() {
        val database = freshDatabase("delivered_at")
        runner(database, "docker")
            .withPropertyValues("spring.flyway.target=2", "spring.jpa.hibernate.ddl-auto=none")
            .run { context -> assertThat(context).hasNotFailed() }
        val deliveredWithShipping = insertOrder(database, "DELIVERED", deliveredAt = null, updatedAt = "2026-10-05 09:00:00")
        insertShipping(database, deliveredWithShipping, "DELIVERED", updatedAt = "2026-10-03 14:30:00", deleted = false)
        val refundedAfterDelivery = insertOrder(database, "REFUNDED", deliveredAt = null, updatedAt = "2026-10-20 10:00:00")
        insertShipping(database, refundedAfterDelivery, "DELIVERED", updatedAt = "2026-10-11 08:15:00", deleted = false)
        val deliveredWithoutShipping = insertOrder(database, "DELIVERED", deliveredAt = null, updatedAt = "2026-10-07 18:45:00")
        val alreadyFilled = insertOrder(database, "DELIVERED", deliveredAt = "2026-10-01 12:00:00", updatedAt = "2026-10-02 12:00:00")
        insertShipping(database, alreadyFilled, "DELIVERED", updatedAt = "2026-10-01 13:00:00", deleted = false)
        val inTransit = insertOrder(database, "SHIPPED", deliveredAt = null, updatedAt = "2026-10-08 11:00:00")
        insertShipping(database, inTransit, "IN_TRANSIT", updatedAt = "2026-10-08 11:00:00", deleted = false)
        val deletedShipping = insertOrder(database, "REFUNDED", deliveredAt = null, updatedAt = "2026-10-09 11:00:00")
        insertShipping(database, deletedShipping, "DELIVERED", updatedAt = "2026-10-09 10:00:00", deleted = true)

        runner(database, "docker").run { context -> assertThat(context).hasNotFailed() }

        assertThat(history(database)).containsExactly("1:SQL:true", "2:SQL:true", "3:SQL:true", "4:SQL:true", "5:SQL:true")
        assertThat(
            jdbc(database).queryForList(
                "SELECT CONCAT_WS('|', id, IFNULL(CAST(delivered_at AS CHAR), 'NULL'), CAST(updated_at AS CHAR)) FROM orders ORDER BY id",
                String::class.java
            )
        ).containsExactly(
            "$deliveredWithShipping|2026-10-03 14:30:00.000000|2026-10-05 09:00:00.000000",
            "$refundedAfterDelivery|2026-10-11 08:15:00.000000|2026-10-20 10:00:00.000000",
            "$deliveredWithoutShipping|2026-10-07 18:45:00.000000|2026-10-07 18:45:00.000000",
            "$alreadyFilled|2026-10-01 12:00:00.000000|2026-10-02 12:00:00.000000",
            "$inTransit|NULL|2026-10-08 11:00:00.000000",
            "$deletedShipping|NULL|2026-10-09 11:00:00.000000"
        )
    }

    private fun insertOrder(database: String, status: String, deliveredAt: String?, updatedAt: String): Long {
        jdbc(database).update(
            "INSERT INTO orders (buyer_id, total_amount, cancel_retry_count, status, delivered_at, created_at, updated_at) " +
                "VALUES (9, 10000.00, 0, ?, ?, '2026-09-30 10:00:00', ?)",
            status,
            deliveredAt,
            updatedAt
        )
        return jdbc(database).queryForObject("SELECT MAX(id) FROM orders", Long::class.java)!!
    }

    private fun insertShipping(database: String, orderId: Long, status: String, updatedAt: String, deleted: Boolean) {
        jdbc(database).update(
            "INSERT INTO shippings (order_id, buyer_id, status, carrier_name, tracking_number, recipient_name, recipient_phone, " +
                "recipient_address, created_at, updated_at, deleted_at) " +
                "VALUES (?, 9, ?, 'carrier', CONCAT('T', ?), 'recipient', '010-0000-0000', 'address', '2026-10-01 09:00:00', ?, ?)",
            orderId,
            status,
            orderId,
            updatedAt,
            if (deleted) updatedAt else null
        )
    }

    private fun insertDlqMessage(database: String, status: String) {
        jdbc(database).update(
            "INSERT INTO dlq_messages (original_topic, original_partition, original_offset, error_timestamp, retry_count, status, created_at) " +
                "VALUES ('kept-topic', 0, 1, 1, 0, ?, NOW(6))",
            status
        )
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
        val database = "order_flyway_${label}_${SEQUENCE.incrementAndGet()}"
        jdbc("mysql").execute("CREATE DATABASE $database CHARACTER SET utf8mb4 COLLATE $COLLATION")
        return database
    }

    private fun jdbc(database: String): JdbcTemplate =
        JdbcTemplate(DriverManagerDataSource(MySqlContainerSupport.jdbcUrl(database), MySqlContainerSupport.username, MySqlContainerSupport.password))

    private fun history(database: String): List<String> =
        jdbc(database).queryForList(
            "SELECT CONCAT(version, ':', type, ':', IF(success, 'true', 'false')) FROM flyway_schema_history ORDER BY installed_rank",
            String::class.java
        )

    private fun databaseCollation(database: String): String? =
        jdbc(database).queryForObject(
            "SELECT DEFAULT_COLLATION_NAME FROM information_schema.SCHEMATA WHERE SCHEMA_NAME = ?",
            String::class.java,
            database
        )

    private fun tableCollations(database: String): List<String> =
        jdbc(database).queryForList(
            "SELECT DISTINCT TABLE_COLLATION FROM information_schema.TABLES WHERE TABLE_SCHEMA = ? AND TABLE_NAME <> 'flyway_schema_history'",
            String::class.java,
            database
        )

    private fun columnCollations(database: String): List<String> =
        jdbc(database).queryForList(
            "SELECT DISTINCT COLLATION_NAME FROM information_schema.COLUMNS " +
                "WHERE TABLE_SCHEMA = ? AND TABLE_NAME <> 'flyway_schema_history' AND COLLATION_NAME IS NOT NULL",
            String::class.java,
            database
        )

    private fun columnType(database: String, table: String, column: String): String? =
        jdbc(database).queryForObject(
            "SELECT COLUMN_TYPE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? AND COLUMN_NAME = ?",
            String::class.java,
            database,
            table,
            column
        )

    private fun columnNullable(database: String, table: String, column: String): String? =
        jdbc(database).queryForObject(
            "SELECT IS_NULLABLE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? AND COLUMN_NAME = ?",
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
            "SELECT CONCAT_WS('|', TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, IFNULL(COLUMN_DEFAULT, 'NULL'), EXTRA, " +
                "IFNULL(COLLATION_NAME, 'NULL')) " +
                "FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = ? AND TABLE_NAME <> 'flyway_schema_history' " +
                "ORDER BY TABLE_NAME, COLUMN_NAME",
            String::class.java,
            database
        )

    private fun indexes(database: String): List<String> =
        jdbc(database).queryForList(
            "SELECT CONCAT_WS('|', TABLE_NAME, INDEX_NAME, NON_UNIQUE, GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX)) " +
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
        private const val COLLATION = "utf8mb4_unicode_ci"
        private val SEQUENCE = AtomicInteger()
        private val LEGACY_ENUM_COLUMNS = listOf(
            LegacyEnumColumn("orders", "status", OrderStatus.entries, nullable = false),
            LegacyEnumColumn("refunds", "status", RefundStatus.entries, nullable = false),
            LegacyEnumColumn("refunds", "reason", RefundReason.entries, nullable = false),
            LegacyEnumColumn("shippings", "status", ShippingStatus.entries, nullable = false),
            LegacyEnumColumn("outbox_events", "status", OutboxStatus.entries, nullable = false),
            LegacyEnumColumn("dlq_messages", "status", DLQStatus.entries, nullable = false)
        )
    }
}

private class LegacyEnumColumn(val table: String, val column: String, values: List<Enum<*>>, val nullable: Boolean) {
    private val definition = "enum(" + values.joinToString(",") { "'${it.name}'" } + ")" + if (nullable) " DEFAULT NULL" else " NOT NULL"

    fun revertToEnum(jdbc: JdbcTemplate) {
        jdbc.execute("ALTER TABLE $table MODIFY $column $definition")
    }
}
