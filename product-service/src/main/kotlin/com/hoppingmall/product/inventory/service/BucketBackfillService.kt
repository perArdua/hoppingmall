package com.hoppingmall.product.inventory.service

import com.hoppingmall.product.inventory.domain.repository.InventoryBucketRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

@Service
@ConditionalOnProperty(name = ["inventory.mode"], havingValue = "bucket")
class BucketBackfillService(
    private val inventoryBucketRepository: InventoryBucketRepository,
    private val bucketStockService: BucketStockService,
    private val inventoryCacheEvictor: InventoryCacheEvictor,
    transactionManager: PlatformTransactionManager,
    @Value("\${inventory.bucket.backfill.batch-size:500}") private val batchSize: Int
) {
    private val logger = LoggerFactory.getLogger(BucketBackfillService::class.java)
    private val transactionTemplate = TransactionTemplate(transactionManager)

    init {
        require(batchSize > 0) { "inventory.bucket.backfill.batch-size must be positive" }
    }

    fun backfill(): BackfillResult {
        var afterProductId = 0L
        var created = 0
        var failed = 0
        while (true) {
            val productIds = inventoryBucketRepository.findProductIdsWithoutBuckets(afterProductId, batchSize).map { it.toLong() }
            productIds.forEach { productId ->
                try {
                    if (createBuckets(productId)) created++
                } catch (e: Exception) {
                    failed++
                    logger.error("버킷 채우기 실패: productId={}", productId, e)
                }
            }
            if (productIds.size < batchSize) break
            afterProductId = productIds.last()
        }
        if (created > 0 || failed > 0) logger.info("버킷 채우기 끝: created={}, failed={}", created, failed)
        return BackfillResult(created, failed)
    }

    private fun createBuckets(productId: Long): Boolean =
        transactionTemplate.execute {
            bucketStockService.createBucketsIfMissing(productId).also { if (it) inventoryCacheEvictor.evictAfterCommit(productId) }
        } ?: false

    data class BackfillResult(val created: Int, val failed: Int)
}
