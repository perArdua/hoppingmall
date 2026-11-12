package com.hoppingmall.order.order.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.dao.DuplicateKeyException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

@Component
class ReservationFence(
    private val jdbcTemplate: JdbcTemplate,
    @Value("\${order.reservation.fence.enabled:false}") val enabled: Boolean
) {

    fun link(reservationIds: Collection<String>): List<String> {
        val fenced = mutableListOf<String>()
        reservationIds.distinct().sorted().forEach { rid ->
            try {
                jdbcTemplate.update(INSERT, rid, LINKED)
            } catch (e: DuplicateKeyException) {
                if (decisionOf(rid) != LINKED) fenced.add(rid)
            }
        }
        return fenced
    }

    fun decide(reservationId: String): String {
        decisionOf(reservationId)?.let { return it }
        return try {
            jdbcTemplate.update(INSERT, reservationId, ABORTED)
            ABORTED
        } catch (e: DuplicateKeyException) {
            decisionOf(reservationId) ?: ABORTED
        }
    }

    fun decisionOf(reservationId: String): String? =
        jdbcTemplate.query(SELECT, { rs, _ -> rs.getString(1) }, reservationId).firstOrNull()

    companion object {
        const val LINKED = "LINKED"
        const val ABORTED = "ABORTED"
        private const val INSERT = "INSERT INTO reservation_fence (reservation_id, decision, created_at) VALUES (?, ?, NOW(6))"
        private const val SELECT = "SELECT decision FROM reservation_fence WHERE reservation_id = ?"
    }
}
