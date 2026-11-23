package com.hoppingmall.order.order.service

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.dao.DuplicateKeyException
import org.springframework.dao.PessimisticLockingFailureException
import org.springframework.jdbc.core.ConnectionCallback
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import java.sql.Connection

@Component
class ReservationFence(
    private val jdbcTemplate: JdbcTemplate,
    @Value("\${order.reservation.fence.enabled:false}") val enabled: Boolean,
    @Value("\${order.reservation.fence.cancel-lock-wait-seconds:1}") private val cancelLockWaitSeconds: Int
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun link(reservationIds: Collection<String>): List<String> {
        val fenced = mutableListOf<String>()
        reservationIds.distinct().sorted().forEach { rid ->
            try {
                jdbcTemplate.update(INSERT, rid, LINKED)
            } catch (e: DuplicateKeyException) {
                fenced.add(rid)
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

    fun abortForCancel(reservationIds: Collection<String>): List<String> =
        reservationIds.distinct().sorted().filter { abortWithinLockWait(it) }

    fun decisionOf(reservationId: String): String? =
        jdbcTemplate.query(SELECT, { rs, _ -> rs.getString(1) }, reservationId).firstOrNull()

    private fun abortWithinLockWait(reservationId: String): Boolean =
        try {
            jdbcTemplate.execute(ConnectionCallback { connection -> insertAborted(connection, reservationId) })
            true
        } catch (e: DuplicateKeyException) {
            val decision = decisionOf(reservationId)
            if (decision != ABORTED) log.info("예약이 이미 주문에 연결되어 취소하지 않음: rid={}, decision={}", reservationId, decision)
            decision == ABORTED
        } catch (e: PessimisticLockingFailureException) {
            log.info("예약 결정이 {}초 안에 나지 않아 취소하지 않고 만료에 맡김: rid={}", cancelLockWaitSeconds, reservationId)
            false
        }

    private fun insertAborted(connection: Connection, reservationId: String) {
        val restoreTo = if (isMySql(connection)) sessionLockWait(connection) else null
        restoreTo?.let { setSessionLockWait(connection, cancelLockWaitSeconds.toLong()) }
        try {
            connection.prepareStatement(INSERT).use { statement ->
                statement.setString(1, reservationId)
                statement.setString(2, ABORTED)
                statement.executeUpdate()
            }
        } finally {
            restoreTo?.let { setSessionLockWait(connection, it) }
        }
    }

    private fun isMySql(connection: Connection): Boolean =
        connection.metaData.databaseProductName.equals(MYSQL, ignoreCase = true)

    private fun sessionLockWait(connection: Connection): Long =
        connection.createStatement().use { statement ->
            statement.executeQuery(SELECT_LOCK_WAIT).use { rs ->
                rs.next()
                rs.getLong(1)
            }
        }

    private fun setSessionLockWait(connection: Connection, seconds: Long) {
        connection.createStatement().use { it.execute("$SET_LOCK_WAIT$seconds") }
    }

    companion object {
        const val LINKED = "LINKED"
        const val ABORTED = "ABORTED"
        private const val MYSQL = "MySQL"
        private const val INSERT = "INSERT INTO reservation_fence (reservation_id, decision, created_at) VALUES (?, ?, NOW(6))"
        private const val SELECT = "SELECT decision FROM reservation_fence WHERE reservation_id = ?"
        private const val SELECT_LOCK_WAIT = "SELECT @@SESSION.innodb_lock_wait_timeout"
        private const val SET_LOCK_WAIT = "SET SESSION innodb_lock_wait_timeout = "
    }
}
