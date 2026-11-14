package com.hoppingmall.product.statistics.service

import com.hoppingmall.product.inventory.domain.Inventory
import com.hoppingmall.product.inventory.domain.repository.InventoryRepository
import com.hoppingmall.product.inventory.service.BucketStockService
import com.hoppingmall.product.product.domain.ProductStatistics
import com.hoppingmall.product.product.domain.repository.ProductStatisticsRepository
import com.hoppingmall.product.product.service.ProductStatisticsCommandService
import com.hoppingmall.product.statistics.port.CartAggregation
import com.hoppingmall.product.statistics.port.CartItemQueryPort
import com.hoppingmall.product.support.withId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.TransactionCallback
import org.springframework.transaction.support.TransactionTemplate

@DisplayName("ProductStatisticsScheduler")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class ProductStatisticsSchedulerTest {

    @Mock
    private lateinit var productStatisticsRepository: ProductStatisticsRepository

    @Mock
    private lateinit var cartItemQueryPort: CartItemQueryPort

    @Mock
    private lateinit var inventoryRepository: InventoryRepository

    @Mock
    private lateinit var bucketStockService: BucketStockService

    @Mock
    private lateinit var productStatisticsCommandService: ProductStatisticsCommandService

    @Mock
    private lateinit var transactionTemplate: TransactionTemplate

    @Mock
    private lateinit var transactionStatus: TransactionStatus

    @Test
    fun 장바구니_및_재고를_동기화한다() {
        val scheduler = ProductStatisticsScheduler(
            productStatisticsRepository, cartItemQueryPort,
            inventoryRepository, bucketStockService, productStatisticsCommandService, transactionTemplate
        )
        val stats = ProductStatistics.create(
            productId = 1L, productName = "테스트", sellerId = 1L, categoryId = 1L
        ).withId(1L)
        val cartAggregations = listOf(CartAggregation(productId = 1L, buyerCount = 5))
        val inventory = Inventory.create(productId = 1L, stockQuantity = 100).withId(1L)

        whenever(cartItemQueryPort.aggregateCartByProduct()).thenReturn(cartAggregations)
        whenever(transactionTemplate.execute(any<TransactionCallback<Pair<List<Long>, Int>>>()))
            .thenAnswer { invocation ->
                @Suppress("UNCHECKED_CAST")
                val callback = invocation.arguments[0] as TransactionCallback<Pair<List<Long>, Int>>
                callback.doInTransaction(transactionStatus)
            }
        whenever(productStatisticsRepository.findIdsAfter(eq(0L), any())).thenReturn(listOf(1L))
        whenever(productStatisticsRepository.findAllByIdInForUpdate(listOf(1L))).thenReturn(listOf(stats))
        whenever(inventoryRepository.findAllByProductIdIn(listOf(1L))).thenReturn(listOf(inventory))

        scheduler.syncCartAndInventory()

        assertThat(stats.currentCartCount).isEqualTo(5)
        assertThat(stats.currentStock).isEqualTo(100)
        verify(productStatisticsCommandService).flushHourlySnapshot()
    }

    @Test
    fun bucket_모드에서는_페이지의_상품들_버킷_합을_한_번에_읽어_통계에_넣는다() {
        val scheduler = ProductStatisticsScheduler(
            productStatisticsRepository, cartItemQueryPort,
            inventoryRepository, bucketStockService, productStatisticsCommandService, transactionTemplate
        )
        val first = ProductStatistics.create(productId = 1L, productName = "테스트", sellerId = 1L, categoryId = 1L).withId(1L)
        val second = ProductStatistics.create(productId = 2L, productName = "테스트", sellerId = 1L, categoryId = 1L).withId(2L)
        val withoutBuckets = ProductStatistics.create(productId = 3L, productName = "테스트", sellerId = 1L, categoryId = 1L).withId(3L)

        whenever(cartItemQueryPort.aggregateCartByProduct()).thenReturn(emptyList())
        whenever(transactionTemplate.execute(any<TransactionCallback<Pair<List<Long>, Int>>>()))
            .thenAnswer { invocation ->
                @Suppress("UNCHECKED_CAST")
                val callback = invocation.arguments[0] as TransactionCallback<Pair<List<Long>, Int>>
                callback.doInTransaction(transactionStatus)
            }
        whenever(productStatisticsRepository.findIdsAfter(eq(0L), any())).thenReturn(listOf(1L, 2L, 3L))
        whenever(productStatisticsRepository.findAllByIdInForUpdate(listOf(1L, 2L, 3L))).thenReturn(listOf(first, second, withoutBuckets))
        whenever(bucketStockService.enabled).thenReturn(true)
        whenever(bucketStockService.totalStocks(listOf(1L, 2L, 3L))).thenReturn(mapOf(1L to 77, 2L to 35))

        scheduler.syncCartAndInventory()

        assertThat(first.currentStock).isEqualTo(77)
        assertThat(second.currentStock).isEqualTo(35)
        assertThat(withoutBuckets.currentStock).isZero()
        verify(bucketStockService, times(1)).totalStocks(any())
        verify(bucketStockService, never()).totalStock(any())
        verify(inventoryRepository, never()).findAllByProductIdIn(any())
    }

    @Test
    fun 일별_통계_마감을_수행한다() {
        val scheduler = ProductStatisticsScheduler(
            productStatisticsRepository, cartItemQueryPort,
            inventoryRepository, bucketStockService, productStatisticsCommandService, transactionTemplate
        )

        scheduler.flushDailyStatistics()

        verify(productStatisticsCommandService).flushDailySnapshot()
    }
}
