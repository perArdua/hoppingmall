package com.hoppingmall.consistency.monitor

import org.springframework.beans.factory.annotation.Value
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

@Component
class MonitorLease(
    private val jdbcTemplate: JdbcTemplate,
    @Value("\${consistency.lease.seconds:300}") private val leaseSeconds: Long,
    @Value("\${consistency.lease.owner:}") owner: String
) {
    fun tryAcquire(): Boolean = true

    fun release() {}
}
