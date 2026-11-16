package com.hoppingmall.consistency.invariant

import com.hoppingmall.consistency.support.MySqlContainerSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import java.math.BigDecimal

@DisplayName("결제 포인트 사용·되돌림 불변식 (실제 MySQL)")
@DisplayNameGeneration(ReplaceUnderscores::class)
class PointUseInvariantMySqlTest {

    private val jdbcTemplate = JdbcTemplate(
        DriverManagerDataSource(MySqlContainerSupport.jdbcUrl("payment_db"), MySqlContainerSupport.username, MySqlContainerSupport.password)
    )

    private val definitions = InvariantSqlParser.parse(
        javaClass.classLoader.getResource("invariants.sql")!!.readText(Charsets.UTF_8),
        mapOf(
            "PENDING_MAX_MINUTES" to "10",
            "PAYING_MAX_MINUTES" to "15",
            "ORPHAN_GRACE_MINUTES" to "1",
            "OUTBOX_MAX_SECONDS" to "60",
            "DLQ_MAX_SECONDS" to "60",
            "POINT_PAYMENT_SINCE" to POINT_PAYMENT_SINCE
        )
    ).associateBy { it.id }

    @BeforeEach
    fun setUp() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS point_histories")
        jdbcTemplate.execute("DROP TABLE IF EXISTS payments")
        jdbcTemplate.execute(
            """
            CREATE TABLE payments (
                id BIGINT PRIMARY KEY,
                order_id BIGINT NOT NULL,
                user_id BIGINT NOT NULL,
                status VARCHAR(32) NOT NULL,
                created_at DATETIME(6) NOT NULL,
                deleted_at DATETIME(6) NULL
            )
            """.trimIndent()
        )
        jdbcTemplate.execute(
            """
            CREATE TABLE point_histories (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                user_id BIGINT NOT NULL,
                payment_id BIGINT NULL,
                type VARCHAR(32) NOT NULL,
                amount DECIMAL(10, 2) NOT NULL,
                event_id VARCHAR(255) NULL UNIQUE,
                created_at DATETIME(6) NOT NULL,
                deleted_at DATETIME(6) NULL
            )
            """.trimIndent()
        )
    }

    @Test
    fun 취소된_결제에_쓴_포인트가_다_돌아오지_않으면_INV_13이_잡는다() {
        payment(1L, "CANCELLED")
        history(1L, "USE", "-3000", "use-1")
        history(1L, "REFUND", "1000", "refund-point-7")
        payment(2L, "CANCELLED")
        history(2L, "USE", "-3000", "use-2")
        history(2L, "REFUND", "1000", "refund-point-8")
        history(2L, "REFUND", "2000", "use-cancel-2")
        payment(3L, "SUCCESS")
        history(3L, "USE", "-3000", "use-3")
        payment(4L, "FAILED")
        history(4L, "USE", "-500", "use-4")

        assertThat(violatingPaymentIds("INV-13")).containsExactlyInAnyOrder(1L, 4L)
    }

    @Test
    fun 쓴_포인트보다_많이_돌려주면_INV_14가_잡는다() {
        payment(1L, "REFUNDED")
        history(1L, "USE", "-3000", "use-1")
        history(1L, "REFUND", "1000", "refund-point-7")
        history(1L, "REFUND", "3000", "refund-point-8")
        payment(2L, "REFUNDED")
        history(2L, "USE", "-3000", "use-2")
        history(2L, "REFUND", "3000", "refund-point-9")

        assertThat(violatingPaymentIds("INV-14")).containsExactly(1L)
    }

    @Test
    fun 기준_시각_전에_만든_결제의_옛_되돌림은_INV_14가_보지_않는다() {
        payment(1L, "REFUNDED", BEFORE)
        history(1L, "REFUND", "3000", "refund-point-1-101")
        payment(2L, "REFUNDED", BEFORE)
        history(2L, "REFUND", "2000", "refund-point-2-102", BEFORE)
        history(9L, "REFUND", "500", "refund-point-9-109", BEFORE)
        payment(3L, "REFUNDED")
        history(3L, "REFUND", "1000", "refund-point-11")

        assertThat(violatingPaymentIds("INV-14")).containsExactly(3L)
    }

    @Test
    fun 포인트_되돌림_이력은_적립_회수로_세지_않아_INV_5가_가려지지_않는다() {
        payment(1L, "CANCELLED")
        history(1L, "EARN", "270", "tx-1")
        history(1L, "USE", "-3000", "use-1")
        history(1L, "REFUND", "3000", "use-cancel-1")
        payment(2L, "CANCELLED")
        history(2L, "EARN", "270", "tx-2")
        history(2L, "RECLAIM", "-270", "refund-2")
        payment(3L, "REFUNDED")
        history(3L, "EARN", "100", "tx-3")
        history(3L, "REFUND", "100", "refund-3-5")

        assertThat(violatingPaymentIds("INV-5")).containsExactly(1L)
    }

    private fun violatingPaymentIds(invariantId: String): List<Long> =
        jdbcTemplate.queryForList(definitions.getValue(invariantId).sql).map { (it["payment_id"] as Number).toLong() }

    private fun payment(id: Long, status: String, createdAt: String = AFTER) {
        jdbcTemplate.update(
            "INSERT INTO payments (id, order_id, user_id, status, created_at) VALUES (?, ?, ?, ?, ?)",
            id, id + 100, USER_ID, status, createdAt
        )
    }

    private fun history(paymentId: Long, type: String, amount: String, eventId: String, createdAt: String = AFTER) {
        jdbcTemplate.update(
            "INSERT INTO point_histories (user_id, payment_id, type, amount, event_id, created_at) VALUES (?, ?, ?, ?, ?, ?)",
            USER_ID, paymentId, type, BigDecimal(amount), eventId, createdAt
        )
    }

    companion object {
        private const val USER_ID = 31L
        private const val POINT_PAYMENT_SINCE = "2026-11-16 06:02:19"
        private const val BEFORE = "2026-11-16 06:02:18"
        private const val AFTER = "2026-11-16 06:02:19"
    }
}
