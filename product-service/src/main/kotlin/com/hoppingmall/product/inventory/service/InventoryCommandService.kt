package com.hoppingmall.product.inventory.service

import com.hoppingmall.product.inventory.dto.request.InventoryInitRequest
import com.hoppingmall.product.inventory.dto.request.InventoryUpdateRequest
import com.hoppingmall.product.inventory.dto.response.InventoryResponse

interface InventoryCommandService {
    fun initStock(request: InventoryInitRequest): InventoryResponse
    fun updateStock(productId: Long, request: InventoryUpdateRequest): InventoryResponse
    fun decreaseStock(productId: Long, quantity: Int)
    fun increaseStock(productId: Long, quantity: Int)
}
