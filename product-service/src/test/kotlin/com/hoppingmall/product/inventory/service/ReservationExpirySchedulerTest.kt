package com.hoppingmall.product.inventory.service

import com.hoppingmall.product.inventory.domain.Inventory
import com.hoppingmall.product.inventory.domain.InventoryReservation
import com.hoppingmall.product.inventory.domain.repository.InventoryRepository
import com.hoppingmall.product.inventory.domain.repository.InventoryReservationRepository
import com.hoppingmall.product.inventory.enums.ReservationStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDateTime

@DisplayName("ReservationExpiryScheduler")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class ReservationExpirySchedulerTest {

    @Mock
    private lateinit var inventoryReservationRepository: InventoryReservationRepository

    @Mock
    private lateinit var inventoryRepository: InventoryRepository

    @InjectMocks
    private lateinit var scheduler: ReservationExpiryScheduler

    @Test
    fun 만료된_예약을_EXPIRED로_전환하고_재고를_원복한다() {
        val reservation = InventoryReservation(
            reservationId = "test-rsv-1",
            productId = 1L,
            quantity = 5,
            status = ReservationStatus.RESERVED,
            expiresAt = LocalDateTime.now().minusMinutes(5)
        )
        val inventory = Inventory.create(productId = 1L, stockQuantity = 0)

        whenever(
            inventoryReservationRepository.findExpiredReservations(
                status = eq(ReservationStatus.RESERVED),
                now = any(),
                limit = any()
            )
        ).thenReturn(listOf(reservation))
        whenever(
            inventoryReservationRepository.updateStatusByCas(
                reservationId = eq("test-rsv-1"),
                expectedStatus = eq(ReservationStatus.RESERVED),
                targetStatus = eq(ReservationStatus.EXPIRED),
                updatedAt = any()
            )
        ).thenReturn(1)
        whenever(inventoryRepository.findByProductIdForUpdate(1L)).thenReturn(inventory)

        scheduler.expireReservations()

        assertThat(inventory.stockQuantity).isEqualTo(5)
        verify(inventoryRepository).findByProductIdForUpdate(1L)
    }

    @Test
    fun CAS_실패_시_재고_원복을_수행하지_않는다() {
        val reservation = InventoryReservation(
            reservationId = "test-rsv-1",
            productId = 1L,
            quantity = 5,
            status = ReservationStatus.RESERVED,
            expiresAt = LocalDateTime.now().minusMinutes(5)
        )

        whenever(
            inventoryReservationRepository.findExpiredReservations(
                status = eq(ReservationStatus.RESERVED),
                now = any(),
                limit = any()
            )
        ).thenReturn(listOf(reservation))
        whenever(
            inventoryReservationRepository.updateStatusByCas(
                reservationId = eq("test-rsv-1"),
                expectedStatus = eq(ReservationStatus.RESERVED),
                targetStatus = eq(ReservationStatus.EXPIRED),
                updatedAt = any()
            )
        ).thenReturn(0)

        scheduler.expireReservations()

        verify(inventoryRepository, never()).findByProductIdForUpdate(any())
    }

    @Test
    fun 만료_예약이_없으면_아무_작업도_수행하지_않는다() {
        whenever(
            inventoryReservationRepository.findExpiredReservations(
                status = eq(ReservationStatus.RESERVED),
                now = any(),
                limit = any()
            )
        ).thenReturn(emptyList())

        scheduler.expireReservations()

        verify(inventoryReservationRepository, never()).updateStatusByCas(any(), any(), any(), any())
        verify(inventoryRepository, never()).findByProductIdForUpdate(any())
    }
}
