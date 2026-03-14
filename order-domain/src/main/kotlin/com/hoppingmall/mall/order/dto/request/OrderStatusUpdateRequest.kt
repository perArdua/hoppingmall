package com.hoppingmall.mall.order.dto.request

import com.hoppingmall.mall.order.enum.OrderStatus
import jakarta.validation.constraints.NotNull

data class OrderStatusUpdateRequest(
    @field:NotNull(message = "주문 상태는 필수입니다")
    val status: OrderStatus
)
