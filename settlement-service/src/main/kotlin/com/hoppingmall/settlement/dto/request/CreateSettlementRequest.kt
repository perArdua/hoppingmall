package com.hoppingmall.settlement.dto.request

import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import java.math.BigDecimal
import java.time.LocalDate

data class CreateSettlementRequest(
    @field:Positive
    val sellerId: Long,

    @field:NotNull
    val periodStart: LocalDate,

    @field:NotNull
    val periodEnd: LocalDate,

    @field:Positive
    @field:DecimalMax("1.0")
    val commissionRate: BigDecimal
)
