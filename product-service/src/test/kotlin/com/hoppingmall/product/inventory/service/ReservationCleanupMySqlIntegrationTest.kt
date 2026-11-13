package com.hoppingmall.product.inventory.service

import com.hoppingmall.product.support.MySqlIntegrationTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import java.sql.Timestamp
import java.time.LocalDateTime

@DisplayName("끝난 재고 예약 정리 MySQL 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class ReservationCleanupMySqlIntegrationTest : MySqlIntegrationTestSupport() {

    private val now: LocalDateTime = LocalDateTime.now()

    @Test
    fun 보존_기간이_지난_취소_만료_예약을_배치로_나눠_모두_지운다() {
        val old = now.minusDays(31)
        listOf("old-cancelled-1", "old-cancelled-2", "old-cancelled-3").forEach { insert(it, "CANCELLED", old, old) }
        listOf("old-expired-1", "old-expired-2").forEach { insert(it, "EXPIRED", old, old) }

        val deleted = ReservationCleanupScheduler(jdbcTemplate, RETENTION_DAYS, 2).cleanUp()

        assertThat(deleted).isEqualTo(5)
        assertThat(remaining()).isEmpty()
    }

    @Test
    fun 진행_중이거나_확정됐거나_보존_기간_안에_끝난_예약은_남긴다() {
        val old = now.minusDays(31)
        insert("old-cancelled", "CANCELLED", old, old)
        insert("old-reserved", "RESERVED", old, old)
        insert("old-claimed", "CLAIMED", old, old)
        insert("old-confirmed", "CONFIRMED", old, old)
        insert("recently-cancelled", "CANCELLED", old, now.minusDays(1))
        insert("recent-expired", "EXPIRED", now.minusDays(2), now.minusDays(2))

        val deleted = ReservationCleanupScheduler(jdbcTemplate, RETENTION_DAYS, 100).cleanUp()

        assertThat(deleted).isEqualTo(1)
        assertThat(remaining()).containsExactlyInAnyOrder(
            "old-reserved", "old-claimed", "old-confirmed", "recently-cancelled", "recent-expired"
        )
    }

    private fun insert(reservationId: String, status: String, expiresAt: LocalDateTime, updatedAt: LocalDateTime) {
        jdbcTemplate.update(
            """
            INSERT INTO inventory_reservations (reservation_id, product_id, quantity, status, expires_at, created_at, updated_at)
            VALUES (?, ?, 1, ?, ?, ?, ?)
            """.trimIndent(),
            reservationId,
            PRODUCT_ID,
            status,
            Timestamp.valueOf(expiresAt),
            Timestamp.valueOf(expiresAt.minusMinutes(10)),
            Timestamp.valueOf(updatedAt)
        )
    }

    private fun remaining(): List<String> =
        jdbcTemplate.queryForList("SELECT reservation_id FROM inventory_reservations", String::class.java)

    companion object {
        private const val PRODUCT_ID = 76_400L
        private const val RETENTION_DAYS = 30L
    }
}
