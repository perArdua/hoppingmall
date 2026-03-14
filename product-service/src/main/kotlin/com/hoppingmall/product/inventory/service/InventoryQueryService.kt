package com.hoppingmall.product.inventory.service

import com.hoppingmall.product.inventory.dto.response.InventoryResponse

interface InventoryQueryService {
    fun getStock(productId: Long): InventoryResponse
}
