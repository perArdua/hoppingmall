package com.hoppingmall.mall.global.adapter

import com.hoppingmall.mall.inventory.api.InventoryCommandPort
import com.hoppingmall.mall.inventory.service.InventoryCommandService
import org.springframework.stereotype.Component

@Component
class InventoryCommandPortAdapter(
    private val inventoryCommandService: InventoryCommandService
) : InventoryCommandPort {

    override fun decreaseStock(productId: Long, quantity: Int) {
        inventoryCommandService.decreaseStock(productId, quantity)
    }

    override fun increaseStock(productId: Long, quantity: Int) {
        inventoryCommandService.increaseStock(productId, quantity)
    }
}
