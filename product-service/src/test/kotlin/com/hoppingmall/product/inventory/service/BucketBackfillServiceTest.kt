package com.hoppingmall.product.inventory.service

import com.hoppingmall.product.inventory.domain.repository.InventoryBucketRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.SimpleTransactionStatus

@DisplayName("BucketBackfillService 단위 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class BucketBackfillServiceTest {

    @Mock
    private lateinit var inventoryBucketRepository: InventoryBucketRepository

    @Mock
    private lateinit var bucketStockService: BucketStockService

    @Mock
    private lateinit var inventoryCacheEvictor: InventoryCacheEvictor

    @Mock
    private lateinit var transactionManager: PlatformTransactionManager

    @Test
    fun 한_상품이_실패해도_나머지_상품을_채우고_실패_수를_돌려준다() {
        whenever(inventoryBucketRepository.findProductIdsWithoutBuckets(0L, 10)).thenReturn(listOf(1L, 2L, 3L))
        whenever(bucketStockService.createBucketsIfMissing(1L)).thenThrow(IllegalStateException("lock wait timeout"))
        whenever(bucketStockService.createBucketsIfMissing(2L)).thenReturn(true)
        whenever(bucketStockService.createBucketsIfMissing(3L)).thenReturn(false)

        val result = service(10).backfill()

        assertThat(result).isEqualTo(BucketBackfillService.BackfillResult(created = 1, failed = 1))
        verify(inventoryCacheEvictor).evictAfterCommit(2L)
        verify(inventoryCacheEvictor, never()).evictAfterCommit(3L)
    }

    @Test
    fun 한_배치가_가득_차면_마지막_상품_id_뒤부터_다음_배치를_읽는다() {
        whenever(inventoryBucketRepository.findProductIdsWithoutBuckets(0L, 2)).thenReturn(listOf(4L, 7L))
        whenever(inventoryBucketRepository.findProductIdsWithoutBuckets(7L, 2)).thenReturn(listOf(9L))
        whenever(bucketStockService.createBucketsIfMissing(4L)).thenReturn(true)
        whenever(bucketStockService.createBucketsIfMissing(7L)).thenReturn(true)
        whenever(bucketStockService.createBucketsIfMissing(9L)).thenReturn(true)

        val result = service(2).backfill()

        assertThat(result.created).isEqualTo(3)
        verify(inventoryBucketRepository).findProductIdsWithoutBuckets(7L, 2)
    }

    private fun service(batchSize: Int): BucketBackfillService {
        whenever(transactionManager.getTransaction(anyOrNull())).thenReturn(SimpleTransactionStatus())
        return BucketBackfillService(inventoryBucketRepository, bucketStockService, inventoryCacheEvictor, transactionManager, batchSize)
    }
}
