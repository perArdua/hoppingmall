package com.hoppingmall.mall.inventory.service

import com.hoppingmall.mall.inventory.dto.response.InventoryResponse

interface InventoryQueryService {
    fun getStock(productId: Long): InventoryResponse
}
