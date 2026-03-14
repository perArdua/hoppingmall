package com.hoppingmall.mall.refund.api

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
