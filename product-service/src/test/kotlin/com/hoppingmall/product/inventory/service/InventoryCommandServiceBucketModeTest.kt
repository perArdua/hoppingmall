package com.hoppingmall.product.inventory.service

import com.hoppingmall.product.inventory.domain.Inventory
import com.hoppingmall.product.inventory.domain.InventoryReservation
import com.hoppingmall.product.inventory.domain.repository.InventoryRepository
import com.hoppingmall.product.inventory.domain.repository.InventoryReservationRepository
import com.hoppingmall.product.inventory.dto.request.InventoryInitRequest
import com.hoppingmall.product.inventory.dto.request.InventoryUpdateRequest
import com.hoppingmall.product.inventory.enums.ReservationStatus
import com.hoppingmall.product.inventory.exception.InventoryBusyException
import com.hoppingmall.product.inventory.exception.InventoryInsufficientStockException
import com.hoppingmall.product.inventory.exception.ReservationConfirmFailedException
import com.hoppingmall.product.product.domain.repository.ProductRepository
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
import org.mockito.Mockito.lenient
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.ObjectProvider
import org.springframework.cache.Cache
import org.springframework.cache.CacheManager
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.SimpleTransactionStatus

@DisplayName("InventoryCommandServiceImpl bucket 모드 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class InventoryCommandServiceBucketModeTest {

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
        lenient().`when`(transactionManager.getTransaction(any())).thenAnswer { SimpleTransactionStatus() }
        lenient().`when`(bucketStockService.enabled).thenReturn(true)
        lenient().`when`(reservationGate.withPermits<Any>(any(), any())).thenAnswer {
            @Suppress("UNCHECKED_CAST")
            (it.arguments[1] as () -> Any).invoke()
        }
        lenient().`when`(inventoryReservationRepository.save(any<InventoryReservation>())).thenAnswer { it.arguments[0] }
        val field = InventoryCommandServiceImpl::class.java.getDeclaredField("ttlSeconds")
        field.isAccessible = true
        field.set(service, 600L)
    }

    @Test
    fun 예약은_세마포어_안에서_버킷_탐색으로_차감하고_재고_행을_잠그지_않는다() {
        val reservationId = service.reserveStock(1L, 2)

        verify(reservationGate).withPermits<Any>(eq(listOf(1L)), any())
        verify(bucketStockService).reserve(eq(1L), eq(2), eq(reservationId))
        verify(bucketStockService, never()).reserveLockingAll(any(), any())
        verify(bucketStockService, never()).recordRestart()
        verify(inventoryRepository, never()).findByProductIdForUpdate(any())
        verify(transactionManager, times(1)).getTransaction(any())
    }

    @Test
    fun 세마포어가_가득_차면_트랜잭션을_시작하지_않고_429를_던진다() {
        whenever(reservationGate.withPermits<Any>(any(), any())).thenThrow(InventoryBusyException())

        assertThatThrownBy { service.reserveStock(1L, 1) }
            .isInstanceOf(InventoryBusyException::class.java)
        verify(transactionManager, never()).getTransaction(any())
    }

    @Test
    fun 배치_예약은_모든_상품의_허가를_얻은_뒤에_트랜잭션을_시작한다() {
        service.batchReserveStock(listOf(2L to 1, 1L to 3))

        verify(reservationGate).withPermits<Any>(eq(listOf(2L, 1L)), any())
    }

    @Test
    fun 탐색이_소진되면_트랜잭션을_롤백하고_전체_잠금으로_한_번_다시_실행한다() {
        whenever(bucketStockService.reserve(eq(1L), eq(1), any())).thenThrow(BucketWalkExhaustedException(1L))

        service.reserveStock(1L, 1)

        verify(transactionManager, times(2)).getTransaction(any())
        verify(transactionManager).rollback(any())
        verify(transactionManager).commit(any())
        verify(bucketStockService).recordRestart()
        verify(bucketStockService).reserveLockingAll(1L, 1)
    }

    @Test
    fun 재실행도_부족하면_재고_부족_예외가_그대로_나간다() {
        whenever(bucketStockService.reserve(eq(1L), eq(1), any())).thenThrow(BucketWalkExhaustedException(1L))
        whenever(bucketStockService.reserveLockingAll(1L, 1)).thenThrow(InventoryInsufficientStockException())

        assertThatThrownBy { service.reserveStock(1L, 1) }
            .isInstanceOf(InventoryInsufficientStockException::class.java)
        verify(bucketStockService, times(1)).reserveLockingAll(1L, 1)
        verify(bucketStockService, times(1)).recordRestart()
        verify(transactionManager, times(2)).rollback(any())
    }

    @Test
    fun 배치는_productId_오름차순으로_예약하고_재실행은_전_상품을_전체_잠금으로_한다() {
        lenient().`when`(bucketStockService.reserve(eq(2L), eq(1), any())).thenThrow(BucketWalkExhaustedException(2L))

        val result = service.batchReserveStock(listOf(2L to 1, 1L to 3))

        assertThat(result.keys).containsExactly(1L, 2L)
        inOrder(bucketStockService) {
            verify(bucketStockService).reserve(eq(1L), eq(3), any())
            verify(bucketStockService).reserve(eq(2L), eq(1), any())
            verify(bucketStockService).reserveLockingAll(1L, 3)
            verify(bucketStockService).reserveLockingAll(2L, 1)
        }
    }

    @Test
    fun 배치_실패_시_bucket_모드는_보상_취소를_하지_않는다() {
        lenient().`when`(bucketStockService.reserve(eq(2L), eq(1), any())).thenThrow(BucketWalkExhaustedException(2L))
        lenient().`when`(bucketStockService.reserveLockingAll(2L, 1)).thenThrow(InventoryInsufficientStockException())

        assertThatThrownBy { service.batchReserveStock(listOf(1L to 1, 2L to 1)) }
            .isInstanceOf(InventoryInsufficientStockException::class.java)
        verify(inventoryReservationRepository, never()).lockCancellable(any())
        verify(inventoryReservationRepository, never()).cancelByIds(any(), any())
        verify(bucketStockService, never()).restore(any(), any())
    }

    @Test
    fun 배치_성공_시_캐시를_비운다() {
        val cacheManager = mock<CacheManager>()
        val cache = mock<Cache>()
        whenever(cacheManagerProvider.getIfAvailable()).thenReturn(cacheManager)
        whenever(cacheManager.getCache("inventory")).thenReturn(cache)

        service.batchReserveStock(listOf(1L to 1))

        verify(cache).evict(1L)
    }

    @Test
    fun 재고_초기화는_버킷도_만든다() {
        whenever(productRepository.existsById(1L)).thenReturn(true)
        whenever(inventoryRepository.existsByProductId(1L)).thenReturn(false)
        whenever(inventoryRepository.save(any<Inventory>())).thenAnswer { (it.arguments[0] as Inventory).withId(1L) }

        service.initStock(InventoryInitRequest(productId = 1L, stockQuantity = 16))

        verify(bucketStockService).initBuckets(1L, 16)
    }

    @Test
    fun 재고_변경은_버킷을_다시_나눈다() {
        whenever(inventoryRepository.findByProductIdForUpdate(1L)).thenReturn(Inventory.create(1L, 5).withId(1L))

        service.updateStock(1L, InventoryUpdateRequest(stockQuantity = 9))

        verify(bucketStockService).redistribute(1L, 9)
    }

    @Test
    fun 직접_차감과_증가는_버킷으로_한다() {
        service.decreaseStock(1L, 2)
        service.increaseStock(1L, 3)

        verify(bucketStockService).decrease(1L, 2)
        verify(bucketStockService).restore(1L, 3)
        verify(inventoryRepository, never()).findByProductIdForUpdate(any())
    }

    @Test
    fun 예약_취소는_버킷에_복구한다() {
        val reservation = InventoryReservation.create(1L, 2, 600)
        val row = arrayOf<Any>(1L, 1L, 2)
        whenever(inventoryReservationRepository.lockCancellable(listOf(reservation.reservationId))).thenReturn(listOf(row))

        service.cancelReservation(reservation.reservationId)

        verify(bucketStockService).restore(1L, 2)
        verify(inventoryRepository, never()).findByProductIdInForUpdate(any())
    }

    @Test
    fun 일괄_취소는_상품별_합계를_버킷에_복구한다() {
        val rows = listOf(
            arrayOf<Any>(1L, 2L, 1),
            arrayOf<Any>(2L, 1L, 2),
            arrayOf<Any>(3L, 2L, 3)
        )
        whenever(inventoryReservationRepository.lockCancellable(any())).thenReturn(rows)

        service.cancelReservations(listOf("rsv-1", "rsv-2", "rsv-3"))

        inOrder(bucketStockService) {
            verify(bucketStockService).restore(1L, 2)
            verify(bucketStockService).restore(2L, 4)
        }
        verify(inventoryRepository, never()).findByProductIdInForUpdate(any())
    }

    @Test
    fun 확정_부분_실패_시_예외를_던지고_버킷을_복구하지_않는다() {
        val first = InventoryReservation.create(1L, 2, 600)
        val second = InventoryReservation.create(2L, 1, 600)
        val ids = listOf(first.reservationId, second.reservationId)

        whenever(
            inventoryReservationRepository.batchUpdateStatusByCas(
                eq(ids),
                eq(ReservationStatus.RESERVED),
                eq(ReservationStatus.CONFIRMED),
                any()
            )
        ).thenReturn(1)
        whenever(inventoryReservationRepository.findByReservationIdIn(ids)).thenReturn(listOf(first))

        assertThatThrownBy { service.confirmReservations(ids) }
            .isInstanceOf(ReservationConfirmFailedException::class.java)
        verify(bucketStockService, never()).restore(any(), any())
    }
}
