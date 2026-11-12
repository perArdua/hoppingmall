package com.hoppingmall.user.support

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future

@DataJpaTest(properties = ["spring.jpa.hibernate.ddl-auto=create-drop"])
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
abstract class MySqlJpaTestSupport {

    @Autowired
    protected lateinit var jdbcTemplate: JdbcTemplate

    protected val executor: ExecutorService = Executors.newCachedThreadPool()

    @BeforeEach
    fun cleanTables() {
        CLEANED_TABLES.forEach { jdbcTemplate.update("DELETE FROM $it") }
    }

    @AfterEach
    fun shutdownExecutor() {
        executor.shutdownNow()
    }

    protected fun awaitLockWaitOrCompletion(pending: Future<*>, actionName: String, waitTable: String) {
        val deadline = System.currentTimeMillis() + LOCK_WAIT_LIMIT_MS
        while (System.currentTimeMillis() < deadline) {
            if (pending.isDone || lockWaitCount(waitTable) > 0) return
            Thread.onSpinWait()
        }
        throw AssertionError("${actionName}가 ${LOCK_WAIT_LIMIT_MS}ms 안에 끝나지도, $waitTable 잠금을 기다리지도 않았다\n" + lockDiagnostics())
    }

    protected fun lockWaitCount(waitTable: String): Long =
        jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) FROM performance_schema.data_lock_waits w
            JOIN performance_schema.data_locks l ON l.ENGINE_LOCK_ID = w.REQUESTING_ENGINE_LOCK_ID
            WHERE l.OBJECT_SCHEMA = DATABASE()
            AND l.OBJECT_NAME = ?
            """.trimIndent(),
            Long::class.java,
            waitTable
        )!!

    private fun lockDiagnostics(): String =
        listOf(
            "innodb_trx=" + jdbcTemplate.queryForList(
                "SELECT trx_id, trx_state, trx_mysql_thread_id, trx_started, trx_query FROM information_schema.innodb_trx"
            ),
            "data_locks=" + jdbcTemplate.queryForList(
                "SELECT ENGINE_TRANSACTION_ID, OBJECT_SCHEMA, OBJECT_NAME, INDEX_NAME, LOCK_TYPE, LOCK_MODE, LOCK_STATUS, LOCK_DATA FROM performance_schema.data_locks"
            )
        ).joinToString("\n")

    companion object {
        private const val LOCK_WAIT_LIMIT_MS = 15_000L
        private const val DATABASE = "user_mysql_test"
        private val CLEANED_TABLES = listOf("sellers", "users")

        @JvmStatic
        @DynamicPropertySource
        fun mysqlProperties(registry: DynamicPropertyRegistry) {
            MySqlContainerSupport.register(registry, DATABASE)
        }
    }
}
