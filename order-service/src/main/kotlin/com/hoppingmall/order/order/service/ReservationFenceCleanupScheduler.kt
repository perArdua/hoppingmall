package com.hoppingmall.order.order.service

import org.springframework.jdbc.core.JdbcTemplate

class ReservationFenceCleanupScheduler(
    private val jdbcTemplate: JdbcTemplate,
    private val enabled: Boolean,
    private val retentionHours: Long,
    private val batchSize: Int
) {

    fun cleanUp(): Int = 0
}
