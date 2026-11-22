package com.hoppingmall.product.inventory.domain.repository

import com.hoppingmall.common.config.TraceCommentStatementInspector
import com.hoppingmall.product.inventory.domain.InventoryReservation
import org.slf4j.LoggerFactory
import org.springframework.dao.DataAccessException
import org.springframework.dao.DuplicateKeyException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.Timestamp

@Repository
class KeyedReservationInserter(
    private val jdbcTemplate: JdbcTemplate
) {

    private val log = LoggerFactory.getLogger(javaClass)

    private val traceComment = TraceCommentStatementInspector()

    fun insert(reservation: InventoryReservation) {
        try {
            jdbcTemplate.update(traceComment.inspect(INSERT_SQL)) { ps ->
                ps.setTimestamp(1, Timestamp.valueOf(reservation.createdAt))
                ps.setTimestamp(2, Timestamp.valueOf(reservation.expiresAt))
                ps.setLong(3, reservation.productId)
                ps.setInt(4, reservation.quantity)
                ps.setString(5, reservation.requestKey)
                ps.setString(6, reservation.reservationId)
                ps.setString(7, reservation.status.name)
                ps.setTimestamp(8, Timestamp.valueOf(reservation.updatedAt))
            }
        } catch (e: DataAccessException) {
            if (!isRequestKeyConflict(e)) log.error("재고 예약 저장 실패: {}", e.mostSpecificCause.message)
            throw e
        }
    }

    companion object {
        private const val REQUEST_KEY_CONSTRAINT = "uk_reservation_request_product"
        private const val INSERT_SQL = """
            INSERT INTO inventory_reservations
                (created_at, expires_at, product_id, quantity, request_key, reservation_id, status, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """

        fun isRequestKeyConflict(e: DataAccessException): Boolean =
            e is DuplicateKeyException &&
                e.mostSpecificCause.message.orEmpty().contains(REQUEST_KEY_CONSTRAINT, ignoreCase = true)
    }
}
