package com.hoppingmall.product.internal

import com.hoppingmall.product.internal.enums.AppliedRequestOperation
import com.hoppingmall.product.internal.service.AppliedRequestKeyGuard
import com.hoppingmall.product.inventory.exception.ReservationConfirmFailedException
import com.hoppingmall.product.inventory.service.InventoryCommandService
import com.hoppingmall.product.inventory.service.ReserveDeadline
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/internal/api/v1/inventory")
class InternalInventoryController(
    private val inventoryCommandService: InventoryCommandService,
    private val appliedRequestKeyGuard: AppliedRequestKeyGuard
) {

    @Value("\${inventory.link-check.enabled:false}")
    private var budgetEnabled: Boolean = false

    @PostMapping("/{productId}/decrease")
    fun decreaseStock(@PathVariable productId: Long, @RequestParam quantity: Int): ResponseEntity<Void> {
        inventoryCommandService.decreaseStock(productId, quantity)
        return ResponseEntity.ok().build()
    }

    @PostMapping("/{productId}/increase")
    fun increaseStock(
        @PathVariable productId: Long,
        @RequestParam quantity: Int,
        @RequestHeader(name = AppliedRequestKeyGuard.REQUEST_KEY_HEADER, required = false) requestKey: String? = null
    ): ResponseEntity<Void> {
        if (requestKey == null) {
            inventoryCommandService.increaseStock(productId, quantity)
        } else {
            appliedRequestKeyGuard.applyOnce(AppliedRequestOperation.INVENTORY_INCREASE, requestKey) {
                inventoryCommandService.increaseStock(productId, quantity)
            }
        }
        return ResponseEntity.ok().build()
    }

    @PostMapping("/{productId}/reserve")
    fun reserveStock(
        @PathVariable productId: Long,
        @RequestParam quantity: Int,
        @RequestHeader(name = REQUEST_KEY_HEADER, required = false) requestKey: String? = null
    ): ResponseEntity<ReservationResponse> {
        val reservationId = if (requestKey != null) {
            inventoryCommandService.batchReserveStock(listOf(productId to quantity), requestKey).getValue(productId)
        } else {
            inventoryCommandService.reserveStock(productId, quantity)
        }
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

    @PostMapping("/reservations/batch-cancel-confirmed")
    fun cancelConfirmedReservations(@RequestBody reservationIds: List<String>): ResponseEntity<Void> {
        inventoryCommandService.cancelConfirmedReservations(reservationIds)
        return ResponseEntity.ok().build()
    }

    @PostMapping("/batch-reserve")
    fun batchReserveStock(
        @RequestBody items: List<ReserveRequest>,
        @RequestHeader(name = ReserveDeadline.HEADER, required = false) budgetMs: Long? = null,
        @RequestHeader(name = REQUEST_KEY_HEADER, required = false) requestKey: String? = null
    ): ResponseEntity<Map<Long, String>> {
        val receivedAt = System.currentTimeMillis()
        if (budgetEnabled && budgetMs != null && budgetMs > 0) ReserveDeadline.set(receivedAt + budgetMs)
        try {
            val pairs = items.map { it.productId to it.quantity }
            val result = if (requestKey != null) {
                inventoryCommandService.batchReserveStock(pairs, requestKey)
            } else {
                inventoryCommandService.batchReserveStock(pairs)
            }
            return ResponseEntity.ok(result)
        } finally {
            ReserveDeadline.clear()
        }
    }

    companion object {
        const val REQUEST_KEY_HEADER = "X-Reservation-Request-Key"
    }

    data class ReserveRequest(val productId: Long, val quantity: Int)
    data class ReservationResponse(val reservationId: String)
    data class ConfirmationResponse(val confirmed: Boolean)
}
