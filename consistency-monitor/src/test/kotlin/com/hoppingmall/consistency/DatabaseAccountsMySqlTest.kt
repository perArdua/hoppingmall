package com.hoppingmall.consistency

import com.hoppingmall.consistency.check.InvariantChecks
import com.hoppingmall.consistency.invariant.InvariantCatalog
import com.hoppingmall.consistency.source.SourceDatabases
import com.hoppingmall.consistency.support.AccountsMySqlContainerSupport
import com.hoppingmall.consistency.support.FlywaySchemas
import com.hoppingmall.consistency.support.MonitorSources
import com.hoppingmall.consistency.violation.ViolationRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource
import org.springframework.core.io.DefaultResourceLoader
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator

@DisplayName("서비스별 DB 계정과 모니터 읽기 계정 (docker/init-accounts.sh로 만든 실제 MySQL)")
@DisplayNameGeneration(ReplaceUnderscores::class)
class DatabaseAccountsMySqlTest {

    private fun jdbc(account: String, database: String): JdbcTemplate =
        JdbcTemplate(DriverManagerDataSource(AccountsMySqlContainerSupport.jdbcUrl(database), account, passwordOf(account)))

    private fun root(): JdbcTemplate = JdbcTemplate(
        DriverManagerDataSource(AccountsMySqlContainerSupport.jdbcUrl(""), "root", AccountsMySqlContainerSupport.ROOT_PASSWORD)
    )

    private fun assertDenied(action: () -> Unit) {
        assertThatThrownBy(action).isInstanceOf(DataAccessException::class.java).rootCause().hasMessageContaining("command denied")
    }

    @Test
    fun 서비스_계정은_자기_DB에_Flyway를_돌렸고_이력에_자기_계정이_남는다() {
        SERVICE_ACCOUNTS.forEach { (account, database, _) ->
            val installedBy = root().queryForList("SELECT DISTINCT installed_by FROM $database.flyway_schema_history", String::class.java)

            assertThat(installedBy).`as`(database).containsExactly(account)
            assertThat(MIGRATED.getValue(database)).`as`(database).isPositive()
        }
    }

    @Test
    fun 서비스_계정은_다른_서비스_DB를_읽지도_쓰지도_못한다() {
        assertDenied { jdbc("order_app", "order_db").queryForList("SELECT id FROM payment_db.payments LIMIT 1") }
        assertDenied { jdbc("payment_app", "payment_db").queryForList("SELECT id FROM order_db.orders LIMIT 1") }
        assertDenied { jdbc("product_app", "product_db").update("DELETE FROM order_db.order_items WHERE id = 0") }
        assertDenied { jdbc("order_app", "order_db").queryForList("SELECT id FROM consistency_db.invariant_violations LIMIT 1") }
    }

    @Test
    fun 모니터_계정은_서비스_DB를_읽기만_하고_consistency_db만_쓴다() {
        val monitor = jdbc("consistency_monitor", "consistency_db")
        MonitorSources.SOURCES.forEach { source ->
            assertThat(monitor.queryForObject("SELECT COUNT(*) FROM ${source}_db.dlq_messages", Long::class.java)).isZero()
        }

        assertDenied { monitor.update("UPDATE order_db.orders SET status = status WHERE id = 0") }
        assertDenied { monitor.update("DELETE FROM payment_db.payments WHERE id = 0") }
        assertDenied { monitor.execute("CREATE TABLE order_db.monitor_probe (id BIGINT)") }
        assertDenied { monitor.queryForList("SELECT id FROM settlement_db.flyway_schema_history LIMIT 1") }

        ResourceDatabasePopulator(ClassPathResource("schema.sql"))
            .execute(DriverManagerDataSource(AccountsMySqlContainerSupport.jdbcUrl("consistency_db"), "consistency_monitor", passwordOf("consistency_monitor")))
        val repository = ViolationRepository(monitor)
        repository.insert("INV-1", "1", "CRITICAL", repository.now(), null, "{}")
        assertThat(repository.findOpen("INV-1")).hasSize(1)
    }

    @Test
    fun 모니터_계정으로_모든_점검과_전체_훑기가_권한_오류_없이_돈다() {
        val properties = MonitorSources.properties(
            MonitorSources.SOURCES.associateWith { AccountsMySqlContainerSupport.jdbcUrl("${it}_db") },
            "consistency_monitor",
            passwordOf("consistency_monitor")
        )
        val sources = SourceDatabases(properties)
        try {
            val catalog = InvariantCatalog(DefaultResourceLoader(), properties)
            val checks = InvariantChecks(catalog, sources, properties)

            catalog.definitions.forEach { definition ->
                assertThat(checks.recent(definition) { listOf("1") }.rows).`as`(definition.id).isEmpty()
                if (checks.isSweepable(definition)) checks.sweep(definition) { throw AssertionError("빈 DB에서 행이 나왔다") }
            }
        } finally {
            sources.close()
        }
    }

    @Test
    fun 계정_스크립트는_이미_계정이_있어도_다시_돌릴_수_있다() {
        val rerun = AccountsMySqlContainerSupport.container.execInContainer("bash", "/docker-entrypoint-initdb.d/init-accounts.sh")

        assertThat(rerun.exitCode).`as`(rerun.stderr).isZero()
        assertThat(jdbc("user_app", "user_db").queryForObject("SELECT CURRENT_USER()", String::class.java)).isEqualTo("user_app@%")
        assertDenied { jdbc("user_app", "user_db").queryForList("SELECT id FROM order_db.orders LIMIT 1") }
    }

    companion object {
        private val SERVICE_ACCOUNTS = listOf(
            Triple("user_app", "user_db", "user-service"),
            Triple("product_app", "product_db", "product-service"),
            Triple("order_app", "order_db", "order-service"),
            Triple("payment_app", "payment_db", "payment-service"),
            Triple("notification_app", "notification_db", "notification-service"),
            Triple("settlement_app", "settlement_db", "settlement-service")
        )

        private val MIGRATED = mutableMapOf<String, Int>()

        private fun passwordOf(account: String): String {
            val variable = if (account == "consistency_monitor") "CONSISTENCY_MONITOR_DB_PASSWORD"
            else account.removeSuffix("_app").uppercase() + "_DB_PASSWORD"
            return AccountsMySqlContainerSupport.passwords.getValue(variable)
        }

        @JvmStatic
        @BeforeAll
        fun migrateAsServiceAccounts() {
            SERVICE_ACCOUNTS.forEach { (account, database, service) ->
                MIGRATED[database] = FlywaySchemas.migrate(
                    AccountsMySqlContainerSupport.jdbcUrl(database), account, passwordOf(account), service
                )
            }
        }
    }
}
