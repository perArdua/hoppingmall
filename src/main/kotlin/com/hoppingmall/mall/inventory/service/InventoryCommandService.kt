package com.hoppingmall.mall.inventory.service

import com.hoppingmall.mall.inventory.dto.request.InventoryInitRequest
import com.hoppingmall.mall.inventory.dto.request.InventoryUpdateRequest
import com.hoppingmall.mall.inventory.dto.response.InventoryResponse

interface InventoryCommandService {
    fun initStock(request: InventoryInitRequest): InventoryResponse
    fun updateStock(productId: Long, request: InventoryUpdateRequest): InventoryResponse
    fun decreaseStock(productId: Long, quantity: Int)
    fun increaseStock(productId: Long, quantity: Int)
}
