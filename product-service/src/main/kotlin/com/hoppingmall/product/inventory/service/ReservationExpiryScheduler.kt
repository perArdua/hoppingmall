package com.hoppingmall.product.inventory.service

import com.hoppingmall.product.inventory.domain.repository.InventoryRepository
import com.hoppingmall.product.inventory.domain.repository.InventoryReservationRepository
import com.hoppingmall.product.inventory.enums.ReservationStatus
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.PageRequest
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
@Profile("!test")
class ReservationExpiryScheduler(
    private val inventoryReservationRepository: InventoryReservationRepository,
    private val inventoryRepository: InventoryRepository
) {
    private val logger = LoggerFactory.getLogger(ReservationExpiryScheduler::class.java)

    @Scheduled(fixedDelay = 60000)
    @Transactional
    fun expireReservations() {
        val expiredReservations = inventoryReservationRepository.findExpiredReservations(
            status = ReservationStatus.RESERVED,
            now = LocalDateTime.now(),
            limit = PageRequest.of(0, 100)
        )

        if (expiredReservations.isEmpty()) return

        logger.info("만료 예약 처리 시작: ${expiredReservations.size}건")

        expiredReservations.sortedBy { it.productId }.forEach { reservation ->
            val updated = inventoryReservationRepository.updateStatusByCas(
                reservationId = reservation.reservationId,
                expectedStatus = ReservationStatus.RESERVED,
                targetStatus = ReservationStatus.EXPIRED,
                updatedAt = LocalDateTime.now()
            )

            if (updated > 0) {
                val inventory = inventoryRepository.findByProductIdForUpdate(reservation.productId)
                if (inventory != null) {
                    inventory.increaseStock(reservation.quantity)
                    logger.info("예약 만료 처리: reservationId=${reservation.reservationId}, " +
                        "productId=${reservation.productId}, quantity=${reservation.quantity}")
                }
            }
        }
    }
}
