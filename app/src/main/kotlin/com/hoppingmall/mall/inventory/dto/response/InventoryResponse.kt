package com.hoppingmall.mall.inventory.dto.response

import com.hoppingmall.mall.inventory.domain.Inventory

data class InventoryResponse(
    val id: Long,
    val productId: Long,
    val stockQuantity: Int
) {
    companion object {
        fun from(inventory: Inventory): InventoryResponse {
            return InventoryResponse(
                id = inventory.id!!,
                productId = inventory.productId,
                stockQuantity = inventory.stockQuantity
            )
        }
    }
}
