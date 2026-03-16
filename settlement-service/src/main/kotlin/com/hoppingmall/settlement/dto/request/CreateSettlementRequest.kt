package com.hoppingmall.settlement.dto.request

import java.math.BigDecimal
import java.time.LocalDate

data class CreateSettlementRequest(
    val sellerId: Long,
    val periodStart: LocalDate,
    val periodEnd: LocalDate,
    val commissionRate: BigDecimal
)
