package com.hoppingmall.product.inventory.service

import com.hoppingmall.product.inventory.domain.Inventory
import com.hoppingmall.product.inventory.domain.InventoryReservation
import com.hoppingmall.product.inventory.domain.repository.InventoryRepository
import com.hoppingmall.product.inventory.domain.repository.InventoryReservationRepository
import com.hoppingmall.product.inventory.enums.ReservationStatus
import com.hoppingmall.product.inventory.exception.InventoryAlreadyExistsException
import com.hoppingmall.product.inventory.exception.InventoryNotFoundException
import com.hoppingmall.product.inventory.exception.ReservationConfirmFailedException
import com.hoppingmall.product.product.domain.repository.ProductRepository
import com.hoppingmall.product.product.exception.ProductNotFoundException
import com.hoppingmall.product.support.withId
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

@DisplayName("InventoryCommandServiceImpl 추가 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class InventoryCommandServiceImplTest {

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
    fun 재고를_초기화한다() {
        val inventory = Inventory.create(productId = 1L, stockQuantity = 100).withId(1L)
        val request = com.hoppingmall.product.inventory.dto.request.InventoryInitRequest(productId = 1L, stockQuantity = 100)

        whenever(productRepository.existsById(1L)).thenReturn(true)
        whenever(inventoryRepository.existsByProductId(1L)).thenReturn(false)
        whenever(inventoryRepository.save(any<Inventory>())).thenReturn(inventory)

        val result = service.initStock(request)

        assertThat(result.stockQuantity).isEqualTo(100)
    }

    @Test
    fun 존재하지_않는_상품에_재고_초기화_시_예외를_발생시킨다() {
        val request = com.hoppingmall.product.inventory.dto.request.InventoryInitRequest(productId = 999L, stockQuantity = 100)

        whenever(productRepository.existsById(999L)).thenReturn(false)

        assertThatThrownBy { service.initStock(request) }
            .isInstanceOf(ProductNotFoundException::class.java)
    }

    @Test
    fun 이미_존재하는_상품에_재고_초기화_시_예외를_발생시킨다() {
        val request = com.hoppingmall.product.inventory.dto.request.InventoryInitRequest(productId = 1L, stockQuantity = 100)

        whenever(productRepository.existsById(1L)).thenReturn(true)
        whenever(inventoryRepository.existsByProductId(1L)).thenReturn(true)

        assertThatThrownBy { service.initStock(request) }
            .isInstanceOf(InventoryAlreadyExistsException::class.java)
    }

    @Test
    fun 재고를_수정한다() {
        val inventory = Inventory.create(productId = 1L, stockQuantity = 100).withId(1L)
        val request = com.hoppingmall.product.inventory.dto.request.InventoryUpdateRequest(stockQuantity = 200)

        whenever(inventoryRepository.findByProductIdForUpdate(1L)).thenReturn(inventory)

        val result = service.updateStock(1L, request)

        assertThat(result.stockQuantity).isEqualTo(200)
    }

    @Test
    fun 존재하지_않는_재고_수정_시_예외를_발생시킨다() {
        val request = com.hoppingmall.product.inventory.dto.request.InventoryUpdateRequest(stockQuantity = 200)

        whenever(inventoryRepository.findByProductIdForUpdate(999L)).thenReturn(null)

        assertThatThrownBy { service.updateStock(999L, request) }
            .isInstanceOf(InventoryNotFoundException::class.java)
    }

    @Test
    fun 재고를_차감한다() {
        val inventory = Inventory.create(productId = 1L, stockQuantity = 100).withId(1L)

        whenever(inventoryRepository.findByProductIdForUpdate(1L)).thenReturn(inventory)

        service.decreaseStock(1L, 10)

        assertThat(inventory.stockQuantity).isEqualTo(90)
    }

    @Test
    fun 존재하지_않는_재고_차감_시_예외를_발생시킨다() {
        whenever(inventoryRepository.findByProductIdForUpdate(999L)).thenReturn(null)

        assertThatThrownBy { service.decreaseStock(999L, 10) }
            .isInstanceOf(InventoryNotFoundException::class.java)
    }

    @Test
    fun 재고를_증가시킨다() {
        val inventory = Inventory.create(productId = 1L, stockQuantity = 100).withId(1L)

        whenever(inventoryRepository.findByProductIdForUpdate(1L)).thenReturn(inventory)

        service.increaseStock(1L, 10)

        assertThat(inventory.stockQuantity).isEqualTo(110)
    }

    @Test
    fun 존재하지_않는_재고_증가_시_예외를_발생시킨다() {
        whenever(inventoryRepository.findByProductIdForUpdate(999L)).thenReturn(null)

        assertThatThrownBy { service.increaseStock(999L, 10) }
            .isInstanceOf(InventoryNotFoundException::class.java)
    }

    @Test
    fun 배치_예약_중_실패_시_이전_예약을_모두_취소한다() {
        val inv1 = Inventory.create(productId = 1L, stockQuantity = 100)
        val inv2 = Inventory.create(productId = 2L, stockQuantity = 0)

        whenever(inventoryRepository.findByProductIdForUpdate(1L)).thenReturn(inv1)
        whenever(inventoryReservationRepository.save(any<InventoryReservation>()))
            .thenAnswer { it.arguments[0] as InventoryReservation }
        whenever(inventoryRepository.findByProductIdForUpdate(2L)).thenReturn(inv2)

        assertThatThrownBy { service.batchReserveStock(listOf(1L to 3, 2L to 5)) }
            .isInstanceOf(Exception::class.java)
    }

    @Test
    fun 배치_예약_성공() {
        val inv1 = Inventory.create(productId = 1L, stockQuantity = 100)
        val inv2 = Inventory.create(productId = 2L, stockQuantity = 100)

        whenever(inventoryRepository.findByProductIdForUpdate(1L)).thenReturn(inv1)
        whenever(inventoryRepository.findByProductIdForUpdate(2L)).thenReturn(inv2)
        whenever(inventoryReservationRepository.save(any<InventoryReservation>()))
            .thenAnswer { it.arguments[0] as InventoryReservation }

        val result = service.batchReserveStock(listOf(2L to 5, 1L to 3))

        assertThat(result).hasSize(2)
        assertThat(result).containsKeys(1L, 2L)
    }

    @Test
    fun 여러_예약을_한번에_취소한다() {
        whenever(inventoryReservationRepository.findByReservationId("rsv-1")).thenReturn(null)
        whenever(inventoryReservationRepository.findByReservationId("rsv-2")).thenReturn(null)

        service.cancelReservations(listOf("rsv-1", "rsv-2"))

        verify(inventoryReservationRepository).findByReservationId("rsv-1")
        verify(inventoryReservationRepository).findByReservationId("rsv-2")
    }

    @Test
    fun 확정_시_일부_이미_확정된_예약이_있으면_성공으로_간주한다() {
        val reservationIds = listOf("rsv-1", "rsv-2")
        val r1 = InventoryReservation(
            reservationId = "rsv-1", productId = 1L, quantity = 3,
            status = ReservationStatus.CONFIRMED, expiresAt = LocalDateTime.now().plusMinutes(10)
        )
        val r2 = InventoryReservation(
            reservationId = "rsv-2", productId = 2L, quantity = 5,
            status = ReservationStatus.CONFIRMED, expiresAt = LocalDateTime.now().plusMinutes(10)
        )

        whenever(
            inventoryReservationRepository.batchUpdateStatusByCas(
                reservationIds = eq(reservationIds),
                expectedStatus = eq(ReservationStatus.RESERVED),
                targetStatus = eq(ReservationStatus.CONFIRMED),
                updatedAt = any()
            )
        ).thenReturn(0)
        whenever(inventoryReservationRepository.findByReservationIdIn(reservationIds)).thenReturn(listOf(r1, r2))

        val result = service.confirmReservations(reservationIds)

        assertThat(result).isTrue()
    }

    @Test
    fun 확정_실패_시_이미_확정된_예약을_롤백하고_재고를_원복한다() {
        val reservationIds = listOf("rsv-1", "rsv-2")
        val r1 = InventoryReservation(
            reservationId = "rsv-1", productId = 1L, quantity = 3,
            status = ReservationStatus.CANCELLED, expiresAt = LocalDateTime.now().plusMinutes(10)
        )

        whenever(
            inventoryReservationRepository.batchUpdateStatusByCas(
                reservationIds = eq(reservationIds),
                expectedStatus = eq(ReservationStatus.RESERVED),
                targetStatus = eq(ReservationStatus.CONFIRMED),
                updatedAt = any()
            )
        ).thenReturn(1)
        whenever(inventoryReservationRepository.findByReservationIdIn(reservationIds)).thenReturn(listOf(r1))
        whenever(
            inventoryReservationRepository.batchUpdateStatusByCas(
                reservationIds = eq(reservationIds),
                expectedStatus = eq(ReservationStatus.CONFIRMED),
                targetStatus = eq(ReservationStatus.CANCELLED),
                updatedAt = any()
            )
        ).thenReturn(1)
        whenever(inventoryReservationRepository.findByReservationId("rsv-1")).thenReturn(r1)
        whenever(inventoryReservationRepository.findByReservationId("rsv-2")).thenReturn(null)
        val inventory = Inventory.create(productId = 1L, stockQuantity = 0)
        whenever(inventoryRepository.findByProductIdForUpdate(1L)).thenReturn(inventory)

        assertThatThrownBy { service.confirmReservations(reservationIds) }
            .isInstanceOf(ReservationConfirmFailedException::class.java)

        assertThat(inventory.stockQuantity).isEqualTo(3)
    }
}
