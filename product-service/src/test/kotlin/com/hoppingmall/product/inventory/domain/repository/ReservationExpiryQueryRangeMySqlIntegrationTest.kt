package com.hoppingmall.product.inventory.domain.repository

import com.hoppingmall.product.support.MySqlIntegrationTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.springframework.data.jpa.repository.Query
import java.sql.Timestamp
import java.time.LocalDateTime
import java.util.UUID

@DisplayName("예약 만료·링크 확인 조회의 읽는 범위 MySQL 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class ReservationExpiryQueryRangeMySqlIntegrationTest : MySqlIntegrationTestSupport() {

    private val now: LocalDateTime = LocalDateTime.now()

    @BeforeEach
    fun seedReservations() {
        val past = now.minusDays(3)
        val finished = (0 until FINISHED_ROWS).map { index ->
            row(FINISHED_STATUSES[index % FINISHED_STATUSES.size], index, past.plusSeconds(index.toLong()), past.minusMinutes(10))
        }
        val active = listOf(
            row("RESERVED", 1, now.minusMinutes(5), now.minusMinutes(20)),
            row("RESERVED", 2, now.minusMinutes(4), now.minusMinutes(20)),
            row("RESERVED", 3, now.minusMinutes(3), now.minusMinutes(20)),
            row("CLAIMED", 4, now.minusMinutes(2), now.minusMinutes(20)),
            row("CLAIMED", 5, now.minusMinutes(1), now.minusMinutes(20)),
            row("RESERVED", 6, now.plusMinutes(10), now.minusMinutes(1)),
            row("RESERVED", 7, now.plusMinutes(10), now.minusMinutes(1))
        )
        jdbcTemplate.batchUpdate(
            """
            INSERT INTO inventory_reservations (reservation_id, product_id, quantity, status, expires_at, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            finished + active
        )
        jdbcTemplate.queryForList("ANALYZE TABLE inventory_reservations")
    }

    @Test
    fun 만료된_예약의_상품별_집계는_끝난_예약_이력을_읽지_않는다() {
        assertReadsOnlyActiveRows("countExpiredGroupByProduct", mapOf("now" to now))
    }

    @Test
    fun 가장_오래된_만료_예약_시각_조회는_끝난_예약_이력을_읽지_않는다() {
        assertReadsOnlyActiveRows("oldestExpiredExpiresAt", mapOf("now" to now))
    }

    @Test
    fun 오래된_RESERVED_예약_조회는_끝난_예약_이력을_읽지_않는다() {
        assertReadsOnlyActiveRows("findReservedCreatedBefore", mapOf("cutoff" to now.minusMinutes(10), "limit" to 100))
    }

    @Test
    fun 만료된_CLAIMED_예약_조회는_끝난_예약_이력을_읽지_않는다() {
        assertReadsOnlyActiveRows("findClaimedExpiredBefore", mapOf("now" to now, "limit" to 100))
    }

    private fun assertReadsOnlyActiveRows(methodName: String, parameters: Map<String, Any>) {
        val sql = InventoryReservationRepository::class.java.methods
            .single { it.name == methodName }
            .getAnnotation(Query::class.java)
            .value
        val names = PARAMETER.findAll(sql).map { it.groupValues[1] }.toList()
        val plan = jdbcTemplate.queryForList(
            "EXPLAIN " + PARAMETER.replace(sql, "?"),
            *names.map { name -> parameters.getValue(name).let { if (it is LocalDateTime) Timestamp.valueOf(it) else it } }.toTypedArray()
        )
        val estimatedRows = plan.sumOf { (it["rows"] as Number?)?.toLong() ?: 0L }

        assertThat(estimatedRows)
            .withFailMessage { "$methodName 가 끝난 예약 이력까지 읽는다(예상 읽기 ${estimatedRows}행, 끝난 행 ${FINISHED_ROWS}개): $plan" }
            .isLessThan(ACTIVE_READ_LIMIT)
    }

    private fun row(status: String, index: Int, expiresAt: LocalDateTime, createdAt: LocalDateTime): Array<Any> =
        arrayOf(
            UUID.randomUUID().toString(),
            PRODUCT_BASE + index % PRODUCTS,
            1,
            status,
            Timestamp.valueOf(expiresAt),
            Timestamp.valueOf(createdAt),
            Timestamp.valueOf(createdAt)
        )

    companion object {
        private const val FINISHED_ROWS = 2_000
        private const val ACTIVE_READ_LIMIT = 100L
        private const val PRODUCTS = 20
        private const val PRODUCT_BASE = 75_500L
        private val FINISHED_STATUSES = listOf("CONFIRMED", "CANCELLED", "EXPIRED")
        private val PARAMETER = Regex(":(\\w+)")
    }
}
