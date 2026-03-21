package com.hoppingmall.product.inventory.service

import com.hoppingmall.product.inventory.domain.Inventory
import com.hoppingmall.product.inventory.domain.InventoryReservation
import com.hoppingmall.product.inventory.domain.repository.InventoryRepository
import com.hoppingmall.product.inventory.domain.repository.InventoryReservationRepository
import com.hoppingmall.product.inventory.dto.request.InventoryInitRequest
import com.hoppingmall.product.inventory.dto.request.InventoryUpdateRequest
import com.hoppingmall.product.inventory.dto.response.InventoryResponse
import com.hoppingmall.product.inventory.enums.ReservationStatus
import com.hoppingmall.product.inventory.exception.InventoryAlreadyExistsException
import com.hoppingmall.product.inventory.exception.InventoryNotFoundException
import com.hoppingmall.product.inventory.exception.ReservationConfirmFailedException
import com.hoppingmall.product.product.domain.repository.ProductRepository
import com.hoppingmall.product.product.exception.ProductNotFoundException
import org.springframework.beans.factory.annotation.Value
import org.springframework.cache.annotation.CacheEvict
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
@Transactional
class InventoryCommandServiceImpl(
    private val inventoryRepository: InventoryRepository,
    private val inventoryReservationRepository: InventoryReservationRepository,
    private val productRepository: ProductRepository
) : InventoryCommandService {

    @Value("\${inventory.reservation.ttl-minutes:10}")
    private var ttlMinutes: Long = 10

    override fun initStock(request: InventoryInitRequest): InventoryResponse {
        if (!productRepository.existsById(request.productId)) {
            throw ProductNotFoundException()
        }

        if (inventoryRepository.existsByProductId(request.productId)) {
            throw InventoryAlreadyExistsException()
        }

        val inventory = Inventory.create(
            productId = request.productId,
            stockQuantity = request.stockQuantity
        )
        val savedInventory = inventoryRepository.save(inventory)
        return InventoryResponse.from(savedInventory)
    }

    @CacheEvict(cacheNames = ["inventory"], key = "#productId")
    override fun updateStock(productId: Long, request: InventoryUpdateRequest): InventoryResponse {
        val inventory = inventoryRepository.findByProductIdForUpdate(productId)
            ?: throw InventoryNotFoundException()

        inventory.stockQuantity = request.stockQuantity
        return InventoryResponse.from(inventory)
    }

    @CacheEvict(cacheNames = ["inventory"], key = "#productId")
    override fun decreaseStock(productId: Long, quantity: Int) {
        val inventory = inventoryRepository.findByProductIdForUpdate(productId)
            ?: throw InventoryNotFoundException()

        inventory.decreaseStock(quantity)
    }

    @CacheEvict(cacheNames = ["inventory"], key = "#productId")
    override fun increaseStock(productId: Long, quantity: Int) {
        val inventory = inventoryRepository.findByProductIdForUpdate(productId)
            ?: throw InventoryNotFoundException()

        inventory.increaseStock(quantity)
    }

    @CacheEvict(cacheNames = ["inventory"], key = "#productId")
    override fun reserveStock(productId: Long, quantity: Int): String {
        val inventory = inventoryRepository.findByProductIdForUpdate(productId)
            ?: throw InventoryNotFoundException()

        inventory.decreaseStock(quantity)

        val reservation = InventoryReservation.create(productId, quantity, ttlMinutes)
        inventoryReservationRepository.save(reservation)
        return reservation.reservationId
    }

    override fun confirmReservations(reservationIds: List<String>): Boolean {
        if (reservationIds.isEmpty()) return true

        val updatedCount = inventoryReservationRepository.batchUpdateStatusByCas(
            reservationIds = reservationIds,
            expectedStatus = ReservationStatus.RESERVED,
            targetStatus = ReservationStatus.CONFIRMED,
            updatedAt = LocalDateTime.now()
        )

        if (updatedCount != reservationIds.size) {
            val reservations = inventoryReservationRepository.findByReservationIdIn(reservationIds)
            val allConfirmed = reservations.size == reservationIds.size &&
                reservations.all { it.status == ReservationStatus.CONFIRMED }
            if (allConfirmed) {
                return true
            }

            if (updatedCount > 0) {
                inventoryReservationRepository.batchUpdateStatusByCas(
                    reservationIds = reservationIds,
                    expectedStatus = ReservationStatus.CONFIRMED,
                    targetStatus = ReservationStatus.CANCELLED,
                    updatedAt = LocalDateTime.now()
                )
                reservationIds.mapNotNull { rsvId -> inventoryReservationRepository.findByReservationId(rsvId) }
                    .filter { it.status == ReservationStatus.CANCELLED }
                    .sortedBy { it.productId }
                    .forEach { reservation ->
                        val inventory = inventoryRepository.findByProductIdForUpdate(reservation.productId) ?: return@forEach
                        inventory.increaseStock(reservation.quantity)
                    }
            }
            throw ReservationConfirmFailedException()
        }
        return true
    }

    override fun cancelReservation(reservationId: String) {
        val reservation = inventoryReservationRepository.findByReservationId(reservationId)
            ?: return

        if (reservation.status != ReservationStatus.RESERVED) {
            return
        }

        val updated = inventoryReservationRepository.updateStatusByCas(
            reservationId = reservationId,
            expectedStatus = ReservationStatus.RESERVED,
            targetStatus = ReservationStatus.CANCELLED,
            updatedAt = LocalDateTime.now()
        )

        if (updated > 0) {
            val inventory = inventoryRepository.findByProductIdForUpdate(reservation.productId)
                ?: return
            inventory.increaseStock(reservation.quantity)
        }
    }

    override fun cancelReservations(reservationIds: List<String>) {
        reservationIds.forEach { cancelReservation(it) }
    }

    override fun batchReserveStock(items: List<Pair<Long, Int>>): Map<Long, String> {
        val sorted = items.sortedBy { it.first }
        val reservationMap = mutableMapOf<Long, String>()
        try {
            sorted.forEach { (productId, quantity) ->
                val reservationId = reserveStock(productId, quantity)
                reservationMap[productId] = reservationId
            }
        } catch (e: Exception) {
            reservationMap.values.forEach { cancelReservation(it) }
            throw e
        }
        return reservationMap
    }
}
