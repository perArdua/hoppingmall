package com.hoppingmall.mall.shipping.dto.request

import com.hoppingmall.mall.shipping.enum.ShippingStatus
import jakarta.validation.constraints.NotNull

data class ShippingStatusUpdateRequest(
    @field:NotNull
    val status: ShippingStatus
)
