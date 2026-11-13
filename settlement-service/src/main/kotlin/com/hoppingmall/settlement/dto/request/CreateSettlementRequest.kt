package com.hoppingmall.settlement.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import java.math.BigDecimal
import java.time.LocalDate

data class CreateSettlementRequest(
    @field:Schema(description = "승인된 판매자의 사용자 id(users.id). 판매자 행 id(sellers.id)가 아니다.")
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
