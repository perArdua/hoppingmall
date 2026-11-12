package com.hoppingmall.product.inventory.service

import com.hoppingmall.product.inventory.domain.Inventory
import com.hoppingmall.product.inventory.domain.InventoryReservation
import com.hoppingmall.product.inventory.domain.repository.InventoryRepository
import com.hoppingmall.product.inventory.domain.repository.InventoryReservationRepository
import com.hoppingmall.product.inventory.enums.ReservationStatus
import com.hoppingmall.product.inventory.exception.InventoryAlreadyExistsException
import com.hoppingmall.product.inventory.exception.InventoryInsufficientStockException
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
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.ObjectProvider
import org.springframework.cache.Cache
import org.springframework.cache.CacheManager
import org.springframework.transaction.support.TransactionSynchronizationManager
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

    @Mock
    private lateinit var cacheManagerProvider: ObjectProvider<CacheManager>

    @InjectMocks
    private lateinit var service: InventoryCommandServiceImpl

    @BeforeEach
    fun setUp() {
        val field = InventoryCommandServiceImpl::class.java.getDeclaredField("ttlSeconds")
        field.isAccessible = true
        field.set(service, 600L)
    }

    @Test
    fun 카오스_실패율이_주입되면_재고_예약이_예외를_던진다() {
        val field = InventoryCommandServiceImpl::class.java.getDeclaredField("chaosFailureRate")
        field.isAccessible = true
        field.set(service, 1.0f)

        assertThatThrownBy { service.reserveStock(1L, 1) }
            .isInstanceOf(RuntimeException::class.java)
            .hasMessageContaining("Chaos injection")
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
    fun 배치_예약이_트랜잭션_밖에서_성공하면_예약한_상품의_재고_캐시를_바로_지운다() {
        val cache = stubInventoryCache()
        stubReservable(1L, 2L)

        service.batchReserveStock(listOf(2L to 5, 1L to 3))

        verify(cache).evict(1L)
        verify(cache).evict(2L)
    }

    @Test
    fun 배치_예약이_트랜잭션_안에서_성공하면_커밋된_뒤에_재고_캐시를_지운다() {
        val cache = stubInventoryCache()
        stubReservable(1L, 2L)
        TransactionSynchronizationManager.initSynchronization()
        try {
            service.batchReserveStock(listOf(2L to 5, 1L to 3))

            verify(cache, never()).evict(any())

            TransactionSynchronizationManager.getSynchronizations().forEach { it.afterCommit() }

            verify(cache).evict(1L)
            verify(cache).evict(2L)
        } finally {
            TransactionSynchronizationManager.clearSynchronization()
        }
    }

    @Test
    fun 배치_예약이_실패하면_재고_캐시를_지우지_않는다() {
        val inv1 = Inventory.create(productId = 1L, stockQuantity = 100)
        val inv2 = Inventory.create(productId = 2L, stockQuantity = 0)
        whenever(inventoryRepository.findByProductIdForUpdate(1L)).thenReturn(inv1)
        whenever(inventoryRepository.findByProductIdForUpdate(2L)).thenReturn(inv2)
        whenever(inventoryReservationRepository.save(any<InventoryReservation>()))
            .thenAnswer { it.arguments[0] as InventoryReservation }

        assertThatThrownBy { service.batchReserveStock(listOf(1L to 3, 2L to 5)) }
            .isInstanceOf(InventoryInsufficientStockException::class.java)

        verify(cacheManagerProvider, never()).getIfAvailable()
    }

    @Test
    fun 캐시_매니저가_없어도_배치_예약은_성공한다() {
        whenever(cacheManagerProvider.getIfAvailable()).thenReturn(null)
        stubReservable(1L)

        val result = service.batchReserveStock(listOf(1L to 3))

        assertThat(result).containsKeys(1L)
    }

    private fun stubInventoryCache(): Cache {
        val cache = mock<Cache>()
        val cacheManager = mock<CacheManager>()
        whenever(cacheManagerProvider.getIfAvailable()).thenReturn(cacheManager)
        whenever(cacheManager.getCache("inventory")).thenReturn(cache)
        return cache
    }

    private fun stubReservable(vararg productIds: Long) {
        productIds.forEach { productId ->
            whenever(inventoryRepository.findByProductIdForUpdate(productId))
                .thenReturn(Inventory.create(productId = productId, stockQuantity = 100))
        }
        whenever(inventoryReservationRepository.save(any<InventoryReservation>()))
            .thenAnswer { it.arguments[0] as InventoryReservation }
    }

    @Test
    fun 여러_예약을_한번에_취소한다() {
        val inv1 = Inventory.create(productId = 1L, stockQuantity = 0)
        val inv2 = Inventory.create(productId = 2L, stockQuantity = 0)

        whenever(inventoryReservationRepository.lockCancellable(listOf("rsv-1", "rsv-2")))
            .thenReturn(listOf(lockedRow(10L, 1L, 3), lockedRow(11L, 2L, 5)))
        whenever(inventoryRepository.findByProductIdInForUpdate(listOf(1L, 2L)))
            .thenReturn(listOf(inv1, inv2))

        service.cancelReservations(listOf("rsv-1", "rsv-2"))

        verify(inventoryReservationRepository).cancelByIds(eq(listOf(10L, 11L)), any())
        assertThat(inv1.stockQuantity).isEqualTo(3)
        assertThat(inv2.stockQuantity).isEqualTo(5)
    }

    @Test
    fun 잠근_시점에_RESERVED인_예약의_수량만_복원한다() {
        val inventory = Inventory.create(productId = 1L, stockQuantity = 0)

        whenever(inventoryReservationRepository.lockCancellable(listOf("rsv-1", "rsv-2", "rsv-3")))
            .thenReturn(listOf(lockedRow(10L, 1L, 3), lockedRow(12L, 1L, 4)))
        whenever(inventoryRepository.findByProductIdInForUpdate(listOf(1L)))
            .thenReturn(listOf(inventory))

        service.cancelReservations(listOf("rsv-1", "rsv-2", "rsv-3"))

        verify(inventoryReservationRepository).cancelByIds(eq(listOf(10L, 12L)), any())
        assertThat(inventory.stockQuantity).isEqualTo(7)
    }

    @Test
    fun 잠글_RESERVED_예약이_없으면_취소를_건너뛴다() {
        whenever(inventoryReservationRepository.lockCancellable(listOf("rsv-1", "rsv-2")))
            .thenReturn(emptyList())

        service.cancelReservations(listOf("rsv-1", "rsv-2"))

        verify(inventoryReservationRepository, never()).cancelByIds(any(), any())
        verify(inventoryRepository, never()).findByProductIdInForUpdate(any())
    }

    @Test
    fun 빈_예약_목록이면_취소를_건너뛴다() {
        service.cancelReservations(emptyList())

        verify(inventoryReservationRepository, never()).lockCancellable(any())
    }

    private fun lockedRow(id: Long, productId: Long, quantity: Int): Array<Any> = arrayOf(id, productId, quantity)

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
