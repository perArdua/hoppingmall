package com.hoppingmall.payment.point.dto.request

import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal

data class PointUseRequest(
    @field:NotNull(message = "사용할 포인트 금액을 입력해주세요")
    @field:DecimalMin(value = "0.01", message = "최소 0.01점 이상 사용 가능합니다")
    val amount: BigDecimal,

    @field:NotNull(message = "주문 ID를 입력해주세요")
    @field:Min(value = 1, message = "유효하지 않은 주문 ID입니다")
    val orderId: Long,

    val reason: String? = null
)
