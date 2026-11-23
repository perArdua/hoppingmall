package com.hoppingmall.order.order.dto.request

import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal

data class OrderExpectedPriceRequest(
    @field:NotNull(message = "장바구니 아이템 ID는 필수입니다")
    val cartItemId: Long,

    @field:NotNull(message = "기대 가격은 필수입니다")
    @field:DecimalMin(value = "0", message = "기대 가격은 0 이상이어야 합니다")
    val productPrice: BigDecimal
)
