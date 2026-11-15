package com.hoppingmall.product.inventory.dto.request

import jakarta.validation.constraints.NotNull

data class InventoryAdjustRequest(
    @field:NotNull(message = "재고 증감 수량은 필수입니다")
    val delta: Int?
)
