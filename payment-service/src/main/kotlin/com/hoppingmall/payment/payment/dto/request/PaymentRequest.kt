package com.hoppingmall.payment.payment.dto.request

import com.hoppingmall.payment.payment.enum.PaymentMethod
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import java.math.BigDecimal

data class PaymentRequest(
    @field:NotNull(message = "주문 ID는 필수입니다.")
    @field:Positive(message = "주문 ID는 양수여야 합니다.")
    val orderId: Long,

    @field:NotNull(message = "결제 금액은 필수입니다.")
    @field:DecimalMin(value = "1", message = "결제 금액은 1 이상이어야 합니다.")
    val amount: BigDecimal,

    @field:NotNull(message = "결제 방법은 필수입니다.")
    val method: PaymentMethod,

    @field:DecimalMin(value = "0.00", message = "포인트 사용 금액은 0 이상이어야 합니다.")
    val pointAmount: BigDecimal = BigDecimal.ZERO,

    val couponId: Long? = null
)
