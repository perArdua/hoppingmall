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
import org.mockito.Mockito.lenient
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.ObjectProvider
import org.springframework.cache.CacheManager
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.SimpleTransactionStatus

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

    @Mock
    private lateinit var bucketStockService: BucketStockService

    @Mock
    private lateinit var reservationGate: InventoryReservationGate

    @Mock
    private lateinit var cacheManagerProvider: ObjectProvider<CacheManager>

    @Mock
    private lateinit var transactionManager: PlatformTransactionManager

    @InjectMocks
    private lateinit var service: InventoryCommandServiceImpl

    @BeforeEach
    fun setUp() {
        lenient().`when`(transactionManager.getTransaction(any())).thenReturn(SimpleTransactionStatus())
        val field = InventoryCommandServiceImpl::class.java.getDeclaredField("ttlSeconds")
        field.isAccessible = true
        field.set(service, 600L)
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

        whenever(inventoryReservationRepository.findIdsByReservationIdIn(reservationIds)).thenReturn(listOf(1L, 2L))
        whenever(
            inventoryReservationRepository.batchConfirmByCas(
                ids = eq(listOf(1L, 2L)),
                expectedStatuses = eq(listOf(ReservationStatus.RESERVED, ReservationStatus.CLAIMED)),
                updatedAt = any()
            )
        ).thenReturn(2)

        val result = service.confirmReservations(reservationIds)

        assertThat(result).isTrue()
    }

    @Test
    fun 배치_확정_일부_만료_시_예외를_발생시킨다() {
        val reservationIds = listOf("rsv-1", "rsv-2")

        whenever(inventoryReservationRepository.findIdsByReservationIdIn(reservationIds)).thenReturn(listOf(1L, 2L))
        whenever(
            inventoryReservationRepository.batchConfirmByCas(
                ids = eq(listOf(1L, 2L)),
                expectedStatuses = eq(listOf(ReservationStatus.RESERVED, ReservationStatus.CLAIMED)),
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
        verify(inventoryReservationRepository, never()).batchConfirmByCas(any(), any(), any())
    }

    @Test
    fun 예약_취소_시_재고가_원복된다() {
        val inventory = Inventory.create(productId = 1L, stockQuantity = 0)

        whenever(inventoryReservationRepository.findIdsByReservationIdIn(listOf("rsv-1"))).thenReturn(listOf(10L))
        whenever(inventoryReservationRepository.lockCancellable(listOf(10L)))
            .thenReturn(listOf(arrayOf<Any>(10L, 1L, 5)))
        whenever(inventoryRepository.findByProductIdInForUpdate(listOf(1L))).thenReturn(listOf(inventory))

        service.cancelReservation("rsv-1")

        verify(inventoryReservationRepository).cancelByIds(eq(listOf(10L)), any())
        assertThat(inventory.stockQuantity).isEqualTo(5)
    }

    @Test
    fun 예약_취소는_예약_행을_잠근_뒤_상태를_바꾸고_재고_행을_잠근다() {
        val inventory = Inventory.create(productId = 1L, stockQuantity = 0)

        whenever(inventoryReservationRepository.findIdsByReservationIdIn(listOf("rsv-1"))).thenReturn(listOf(10L))
        whenever(inventoryReservationRepository.lockCancellable(listOf(10L)))
            .thenReturn(listOf(arrayOf<Any>(10L, 1L, 5)))
        whenever(inventoryRepository.findByProductIdInForUpdate(listOf(1L))).thenReturn(listOf(inventory))

        service.cancelReservation("rsv-1")

        val order = inOrder(inventoryReservationRepository, inventoryRepository)
        order.verify(inventoryReservationRepository).lockCancellable(listOf(10L))
        order.verify(inventoryReservationRepository).cancelByIds(eq(listOf(10L)), any())
        order.verify(inventoryRepository).findByProductIdInForUpdate(listOf(1L))
    }

    @Test
    fun 예약_취소_시_재고_행이_없으면_예약만_취소된다() {
        whenever(inventoryReservationRepository.findIdsByReservationIdIn(listOf("rsv-1"))).thenReturn(listOf(10L))
        whenever(inventoryReservationRepository.lockCancellable(listOf(10L)))
            .thenReturn(listOf(arrayOf<Any>(10L, 1L, 5)))
        whenever(inventoryRepository.findByProductIdInForUpdate(listOf(1L))).thenReturn(emptyList())

        service.cancelReservation("rsv-1")

        verify(inventoryReservationRepository).cancelByIds(eq(listOf(10L)), any())
    }

    @Test
    fun 취소할_수_있는_예약이_없으면_예약_취소는_무시된다() {
        whenever(inventoryReservationRepository.findIdsByReservationIdIn(listOf("rsv-1"))).thenReturn(listOf(10L))
        whenever(inventoryReservationRepository.lockCancellable(listOf(10L))).thenReturn(emptyList())

        service.cancelReservation("rsv-1")

        verify(inventoryReservationRepository, never()).cancelByIds(any(), any())
        verify(inventoryRepository, never()).findByProductIdInForUpdate(any())
    }
}
