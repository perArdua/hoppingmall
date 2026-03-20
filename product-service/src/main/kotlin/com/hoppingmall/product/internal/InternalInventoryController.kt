package com.hoppingmall.product.internal

import com.hoppingmall.product.inventory.exception.ReservationConfirmFailedException
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

    @PostMapping("/{productId}/reserve")
    fun reserveStock(
        @PathVariable productId: Long,
        @RequestParam quantity: Int
    ): ResponseEntity<ReservationResponse> {
        val reservationId = inventoryCommandService.reserveStock(productId, quantity)
        return ResponseEntity.ok(ReservationResponse(reservationId))
    }

    @PostMapping("/reservations/batch-confirm")
    fun confirmReservations(
        @RequestBody reservationIds: List<String>
    ): ResponseEntity<ConfirmationResponse> {
        return try {
            inventoryCommandService.confirmReservations(reservationIds)
            ResponseEntity.ok(ConfirmationResponse(true))
        } catch (e: ReservationConfirmFailedException) {
            ResponseEntity.ok(ConfirmationResponse(false))
        }
    }

    @PostMapping("/reservations/{reservationId}/cancel")
    fun cancelReservation(@PathVariable reservationId: String): ResponseEntity<Void> {
        inventoryCommandService.cancelReservation(reservationId)
        return ResponseEntity.ok().build()
    }

    @PostMapping("/reservations/batch-cancel")
    fun cancelReservations(@RequestBody reservationIds: List<String>): ResponseEntity<Void> {
        inventoryCommandService.cancelReservations(reservationIds)
        return ResponseEntity.ok().build()
    }

    @PostMapping("/batch-reserve")
    fun batchReserveStock(@RequestBody items: List<ReserveRequest>): ResponseEntity<Map<Long, String>> {
        val pairs = items.map { it.productId to it.quantity }
        val result = inventoryCommandService.batchReserveStock(pairs)
        return ResponseEntity.ok(result)
    }

    data class ReserveRequest(val productId: Long, val quantity: Int)
    data class ReservationResponse(val reservationId: String)
    data class ConfirmationResponse(val confirmed: Boolean)
}
