package com.hoppingmall.consistency.monitor

import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.dao.DataAccessException
import org.springframework.dao.DuplicateKeyException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.net.InetAddress
import java.util.UUID

@Component
class MonitorLease(
    private val jdbcTemplate: JdbcTemplate,
    @Value("\${consistency.lease.seconds:60}") private val leaseSeconds: Long,
    @Value("\${consistency.lease.owner:}") owner: String
) {
    private val log = LoggerFactory.getLogger(javaClass)

    val owner: String = owner.ifBlank { "${hostName()}-${UUID.randomUUID()}" }

    @Volatile
    private var released = false

    fun tryAcquire(): Boolean {
        if (jdbcTemplate.update(CLAIM, owner, leaseSeconds, NAME, owner) == 1) return true
        return try {
            jdbcTemplate.update(INSERT, NAME, owner, leaseSeconds) == 1
        } catch (e: DuplicateKeyException) {
            false
        }
    }

    @Scheduled(
        initialDelayString = "\${consistency.lease.renew-ms:20000}",
        fixedDelayString = "\${consistency.lease.renew-ms:20000}"
    )
    fun renew() {
        if (released) return
        try {
            jdbcTemplate.update(RENEW, leaseSeconds, NAME, owner)
        } catch (e: DataAccessException) {
            log.warn("점검 임대 갱신 실패, 다음 갱신이나 점검 주기에 다시 시도: {}", e.message)
        }
    }

    @PreDestroy
    fun release() {
        released = true
        try {
            jdbcTemplate.update(RELEASE, NAME, owner)
        } catch (e: DataAccessException) {
            log.warn("점검 임대 반납 실패, 임대 시간이 지나면 다른 인스턴스가 이어받음: {}", e.message)
        }
    }

    private fun hostName(): String =
        runCatching { InetAddress.getLocalHost().hostName }.getOrDefault("monitor")

    companion object {
        private const val NAME = "consistency-monitor"
        private const val CLAIM =
            "UPDATE monitor_leases SET owner = ?, lease_until = NOW(6) + INTERVAL ? SECOND " +
                "WHERE name = ? AND (owner = ? OR lease_until <= NOW(6))"
        private const val INSERT =
            "INSERT INTO monitor_leases (name, owner, lease_until) VALUES (?, ?, NOW(6) + INTERVAL ? SECOND)"
        private const val RENEW =
            "UPDATE monitor_leases SET lease_until = NOW(6) + INTERVAL ? SECOND WHERE name = ? AND owner = ?"
        private const val RELEASE =
            "UPDATE monitor_leases SET lease_until = NOW(6) WHERE name = ? AND owner = ?"
    }
}
