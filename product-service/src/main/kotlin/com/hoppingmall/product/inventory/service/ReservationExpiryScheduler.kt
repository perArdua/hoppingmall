package com.hoppingmall.product.inventory.service

import com.hoppingmall.product.inventory.domain.repository.InventoryRepository
import com.hoppingmall.product.inventory.domain.repository.InventoryReservationRepository
import com.hoppingmall.product.inventory.enums.ReservationStatus
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.PageRequest
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
@Profile("!test")
@ConditionalOnProperty(name = ["inventory.mode"], havingValue = "single", matchIfMissing = true)
class ReservationExpiryScheduler(
    private val inventoryReservationRepository: InventoryReservationRepository,
    private val inventoryRepository: InventoryRepository
) {
    private val logger = LoggerFactory.getLogger(ReservationExpiryScheduler::class.java)

    @Value("\${inventory.expiry.enabled:true}")
    private var expiryEnabled: Boolean = true

    @Value("\${inventory.expiry.limit:100}")
    private var expiryLimit: Int = 100

    @Autowired
    private var inventoryCacheEvictor: InventoryCacheEvictor? = null

    @Scheduled(fixedDelayString = "\${inventory.reservation.expiry-interval-ms:60000}")
    @Transactional
    fun expireReservations() {
        if (!expiryEnabled) return

        val expiredReservations = inventoryReservationRepository.findExpiredReservations(
            status = ReservationStatus.RESERVED,
            now = LocalDateTime.now(),
            limit = PageRequest.of(0, expiryLimit)
        )

        if (expiredReservations.isEmpty()) return

        logger.info("만료 예약 처리 시작: ${expiredReservations.size}건")

        expiredReservations.sortedBy { it.productId }
            .groupBy { it.productId }
            .forEach { (productId, reservations) ->
                val expiredQuantity = reservations.sumOf { reservation ->
                    val updated = inventoryReservationRepository.updateStatusByCas(
                        reservationId = reservation.reservationId,
                        expectedStatus = ReservationStatus.RESERVED,
                        targetStatus = ReservationStatus.EXPIRED,
                        updatedAt = LocalDateTime.now()
                    )
                    if (updated > 0) {
                        logger.info("예약 만료 처리: reservationId=${reservation.reservationId}, " +
                            "productId=${reservation.productId}, quantity=${reservation.quantity}")
                        reservation.quantity
                    } else {
                        0
                    }
                }
                if (expiredQuantity > 0) {
                    val inventory = inventoryRepository.findByProductIdForUpdate(productId)
                    inventory?.increaseStock(expiredQuantity)
                    inventoryCacheEvictor?.evictAfterCommit(productId)
                }
            }
    }
}
