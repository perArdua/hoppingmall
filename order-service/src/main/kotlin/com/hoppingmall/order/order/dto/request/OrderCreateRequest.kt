package com.hoppingmall.order.order.dto.request

import jakarta.validation.constraints.NotEmpty

data class OrderCreateRequest(
    @field:NotEmpty(message = "장바구니 아이템 ID 목록은 비어있을 수 없습니다")
    val cartItemIds: List<Long>
)
