package com.hoppingmall.product.internal

import com.hoppingmall.product.inventory.service.InventoryCommandService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/internal/api/v1/inventory")
class InternalInventoryController(
    private val inventoryCommandService: InventoryCommandService
) {

    @PostMapping("/{productId}/decrease")
    fun decreaseStock(@PathVariable productId: Long, @RequestParam quantity: Int): ResponseEntity<Void> {
        inventoryCommandService.decreaseStock(productId, quantity)
        return ResponseEntity.ok().build()
    }

    @PostMapping("/{productId}/increase")
    fun increaseStock(@PathVariable productId: Long, @RequestParam quantity: Int): ResponseEntity<Void> {
        inventoryCommandService.increaseStock(productId, quantity)
        return ResponseEntity.ok().build()
    }
}
