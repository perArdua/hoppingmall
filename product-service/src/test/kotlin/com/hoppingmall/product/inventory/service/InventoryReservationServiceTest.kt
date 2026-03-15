package com.hoppingmall.product.inventory.service

import com.hoppingmall.product.inventory.domain.Inventory
import com.hoppingmall.product.inventory.domain.InventoryReservation
import com.hoppingmall.product.inventory.domain.repository.InventoryRepository
import com.hoppingmall.product.inventory.domain.repository.InventoryReservationRepository
import com.hoppingmall.product.inventory.enums.ReservationStatus
import com.hoppingmall.product.inventory.exception.ReservationConfirmFailedException
import com.hoppingmall.product.product.domain.repository.ProductRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
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

@DisplayName("InventoryCommandServiceImpl")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class InventoryReservationServiceTest {

    @Mock
    private lateinit var inventoryRepository: InventoryRepository

    @Mock
    private lateinit var inventoryReservationRepository: InventoryReservationRepository

    @Mock
    private lateinit var productRepository: ProductRepository

    @InjectMocks
    private lateinit var service: InventoryCommandServiceImpl

    @BeforeEach
    fun setUp() {
        val field = InventoryCommandServiceImpl::class.java.getDeclaredField("ttlMinutes")
        field.isAccessible = true
        field.set(service, 10L)
    }

    @Test
    fun 재고_예약_성공_시_예약_ID를_반환한다() {
        val inventory = Inventory.create(productId = 1L, stockQuantity = 10)

        whenever(inventoryRepository.findByProductIdForUpdate(1L)).thenReturn(inventory)
        whenever(inventoryReservationRepository.save(any<InventoryReservation>())).thenAnswer { it.arguments[0] as InventoryReservation }

        val result = service.reserveStock(1L, 3)

        assertThat(result).isNotBlank()
        verify(inventoryReservationRepository).save(any<InventoryReservation>())
    }

    @Test
    fun 재고_부족_시_예외를_발생시킨다() {
        val inventory = Inventory.create(productId = 1L, stockQuantity = 0)

        whenever(inventoryRepository.findByProductIdForUpdate(1L)).thenReturn(inventory)

        assertThatThrownBy { service.reserveStock(1L, 5) }
            .isInstanceOf(Exception::class.java)

        verify(inventoryReservationRepository, never()).save(any<InventoryReservation>())
    }

    @Test
    fun 배치_확정_성공_시_true를_반환한다() {
        val reservationIds = listOf("rsv-1", "rsv-2")

        whenever(
            inventoryReservationRepository.batchUpdateStatusByCas(
                reservationIds = eq(reservationIds),
                expectedStatus = eq(ReservationStatus.RESERVED),
                targetStatus = eq(ReservationStatus.CONFIRMED),
                updatedAt = any()
            )
        ).thenReturn(2)

        val result = service.confirmReservations(reservationIds)

        assertThat(result).isTrue()
    }

    @Test
    fun 배치_확정_일부_만료_시_예외를_발생시킨다() {
        val reservationIds = listOf("rsv-1", "rsv-2")

        whenever(
            inventoryReservationRepository.batchUpdateStatusByCas(
                reservationIds = eq(reservationIds),
                expectedStatus = eq(ReservationStatus.RESERVED),
                targetStatus = eq(ReservationStatus.CONFIRMED),
                updatedAt = any()
            )
        ).thenReturn(1)

        assertThatThrownBy { service.confirmReservations(reservationIds) }
            .isInstanceOf(ReservationConfirmFailedException::class.java)
    }

    @Test
    fun 빈_목록_배치_확정_시_true를_반환한다() {
        val result = service.confirmReservations(emptyList())

        assertThat(result).isTrue()
        verify(inventoryReservationRepository, never()).batchUpdateStatusByCas(any(), any(), any(), any())
    }

    @Test
    fun 예약_취소_시_재고가_원복된다() {
        val reservation = InventoryReservation(
            reservationId = "rsv-1",
            productId = 1L,
            quantity = 5,
            status = ReservationStatus.RESERVED,
            expiresAt = LocalDateTime.now().plusMinutes(10)
        )
        val inventory = Inventory.create(productId = 1L, stockQuantity = 0)

        whenever(inventoryReservationRepository.findByReservationId("rsv-1")).thenReturn(reservation)
        whenever(
            inventoryReservationRepository.updateStatusByCas(
                reservationId = eq("rsv-1"),
                expectedStatus = eq(ReservationStatus.RESERVED),
                targetStatus = eq(ReservationStatus.CANCELLED),
                updatedAt = any()
            )
        ).thenReturn(1)
        whenever(inventoryRepository.findByProductIdForUpdate(1L)).thenReturn(inventory)

        service.cancelReservation("rsv-1")

        assertThat(inventory.stockQuantity).isEqualTo(5)
    }

    @Test
    fun 이미_취소된_예약_취소는_무시된다() {
        val reservation = InventoryReservation(
            reservationId = "rsv-1",
            productId = 1L,
            quantity = 5,
            status = ReservationStatus.CANCELLED,
            expiresAt = LocalDateTime.now().plusMinutes(10)
        )

        whenever(inventoryReservationRepository.findByReservationId("rsv-1")).thenReturn(reservation)

        service.cancelReservation("rsv-1")

        verify(inventoryReservationRepository, never()).updateStatusByCas(any(), any(), any(), any())
        verify(inventoryRepository, never()).findByProductIdForUpdate(any())
    }

    @Test
    fun 이미_만료된_예약_취소는_무시된다() {
        val reservation = InventoryReservation(
            reservationId = "rsv-1",
            productId = 1L,
            quantity = 5,
            status = ReservationStatus.EXPIRED,
            expiresAt = LocalDateTime.now().minusMinutes(5)
        )

        whenever(inventoryReservationRepository.findByReservationId("rsv-1")).thenReturn(reservation)

        service.cancelReservation("rsv-1")

        verify(inventoryReservationRepository, never()).updateStatusByCas(any(), any(), any(), any())
        verify(inventoryRepository, never()).findByProductIdForUpdate(any())
    }

    @Test
    fun 존재하지_않는_예약_취소는_무시된다() {
        whenever(inventoryReservationRepository.findByReservationId("rsv-unknown")).thenReturn(null)

        service.cancelReservation("rsv-unknown")

        verify(inventoryReservationRepository, never()).updateStatusByCas(any(), any(), any(), any())
        verify(inventoryRepository, never()).findByProductIdForUpdate(any())
    }
}
