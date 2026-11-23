package com.hoppingmall.product.review.service

import com.hoppingmall.product.product.domain.repository.ProductRepository
import com.hoppingmall.product.review.domain.repository.ReviewRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.cache.CacheManager
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.math.RoundingMode

@Service
class ProductReviewSummaryService(
    private val productRepository: ProductRepository,
    private val reviewRepository: ReviewRepository,
    private val cacheManagerProvider: ObjectProvider<CacheManager>,
    transactionManager: PlatformTransactionManager,
    @Value("\${product.review-summary.backfill.batch-size:500}") private val batchSize: Int
) {
    private val logger = LoggerFactory.getLogger(ProductReviewSummaryService::class.java)
    private val transactionTemplate = TransactionTemplate(transactionManager).apply {
        isolationLevel = TransactionDefinition.ISOLATION_READ_COMMITTED
    }

    init {
        require(batchSize > 0) { "product.review-summary.backfill.batch-size must be positive" }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    fun refresh(productId: Long) {
        productRepository.lockIdForUpdate(productId) ?: return
        val reviewCount = reviewRepository.countByProductId(productId)
        val averageRating = reviewRepository.averageRatingByProductId(productId)
            ?.let { BigDecimal(it).setScale(2, RoundingMode.HALF_UP) }
            ?: BigDecimal.ZERO.setScale(2)
        productRepository.updateReviewSummary(productId, reviewCount, averageRating)
        evictProductAfterCommit(productId)
    }

    fun backfill(): BackfillResult {
        var afterProductId = 0L
        var refreshed = 0
        var failed = 0
        while (true) {
            val productIds = productRepository.findIdsAfter(afterProductId, batchSize).map { it.toLong() }
            productIds.forEach { productId ->
                try {
                    transactionTemplate.executeWithoutResult { refresh(productId) }
                    refreshed++
                } catch (e: Exception) {
                    failed++
                    logger.error("상품 리뷰 집계 채우기 실패: productId={}", productId, e)
                }
            }
            if (productIds.size < batchSize) break
            afterProductId = productIds.last()
        }
        logger.info("상품 리뷰 집계 채우기 끝: refreshed={}, failed={}", refreshed, failed)
        return BackfillResult(refreshed, failed)
    }

    private fun evictProductAfterCommit(productId: Long) {
        val cache = cacheManagerProvider.getIfAvailable()?.getCache(PRODUCT_CACHE) ?: return
        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCommit() {
                cache.evict(productId)
            }
        })
    }

    data class BackfillResult(val refreshed: Int, val failed: Int)

    companion object {
        private const val PRODUCT_CACHE = "product"
    }
}
