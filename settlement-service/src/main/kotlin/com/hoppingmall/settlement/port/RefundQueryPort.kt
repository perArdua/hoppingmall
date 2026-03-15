package com.hoppingmall.settlement.port

import java.math.BigDecimal
import java.time.LocalDateTime

interface RefundQueryPort {
    fun findCompletedBySellerAndPeriod(
        sellerId: Long,
        startDate: LocalDateTime,
        endDate: LocalDateTime
    ): List<RefundInfo>
}

data class RefundInfo(
    val id: Long,
    val refundAmount: BigDecimal
)
