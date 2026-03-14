package com.hoppingmall.order.shipping.dto.request

import com.hoppingmall.order.shipping.enum.ShippingStatus
import jakarta.validation.constraints.NotNull

data class ShippingStatusUpdateRequest(
    @field:NotNull
    val status: ShippingStatus
)
