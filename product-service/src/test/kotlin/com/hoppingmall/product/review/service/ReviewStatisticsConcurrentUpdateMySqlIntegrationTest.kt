package com.hoppingmall.product.review.service

import com.hoppingmall.product.product.domain.ProductStatistics
import com.hoppingmall.product.product.domain.repository.ProductStatisticsRepository
import com.hoppingmall.product.review.domain.Review
import com.hoppingmall.product.review.domain.repository.ReviewRepository
import com.hoppingmall.product.review.dto.request.ReviewUpdateRequest
import com.hoppingmall.product.support.MySqlIntegrationTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.math.BigDecimal
import java.util.concurrent.TimeUnit

@DisplayName("리뷰 통계 동시 갱신 MySQL 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class ReviewStatisticsConcurrentUpdateMySqlIntegrationTest : MySqlIntegrationTestSupport() {

    @Autowired
    private lateinit var reviewCommandService: ReviewCommandService

    @Autowired
    private lateinit var reviewRepository: ReviewRepository

    @Autowired
    private lateinit var productStatisticsRepository: ProductStatisticsRepository

    @BeforeEach
    @AfterEach
    fun cleanReviewsAndStatistics() {
        jdbcTemplate.update("DELETE FROM reviews WHERE product_id = ?", PRODUCT_ID)
        jdbcTemplate.update("DELETE FROM product_statistics WHERE product_id = ?", PRODUCT_ID)
    }

    @Test
    fun 같은_상품의_리뷰_수정_두_건이_동시에_오면_평균_평점에_둘_다_반영한다() {
        seedStatistics()
        val reviews = seedReviews()

        runConcurrentlyWhileStatisticsLocked(
            { reviewCommandService.updateReview(reviews[0].id!!, BUYER_IDS[0], ReviewUpdateRequest(1, "평점을 낮춥니다 첫째")) },
            { reviewCommandService.updateReview(reviews[1].id!!, BUYER_IDS[1], ReviewUpdateRequest(1, "평점을 낮춥니다 둘째")) }
        )

        val stats = stats()
        assertThat(stats.reviewCount).isEqualTo(3L)
        assertThat(stats.averageRating).isEqualByComparingTo("2.33")
    }

    @Test
    fun 같은_상품의_리뷰_삭제_두_건이_동시에_오면_리뷰_수에_둘_다_반영한다() {
        seedStatistics()
        val reviews = seedReviews()

        runConcurrentlyWhileStatisticsLocked(
            { reviewCommandService.deleteReview(reviews[0].id!!, BUYER_IDS[0]) },
            { reviewCommandService.deleteReview(reviews[1].id!!, BUYER_IDS[1]) }
        )

        val stats = stats()
        assertThat(stats.reviewCount).isEqualTo(1L)
        assertThat(stats.averageRating).isEqualByComparingTo("5.00")
    }

    @Test
    fun 리뷰_통계_갱신이_그사이_커밋된_판매_가산을_지우지_않는다() {
        seedStatistics()
        val reviews = seedReviews()

        val result = runWhileHolding(
            holdingChanges = { holder ->
                holder.prepareStatement(
                    "UPDATE product_statistics SET total_sales_quantity = total_sales_quantity + 5, order_count = order_count + 1 WHERE product_id = ?"
                ).use {
                    it.setLong(1, PRODUCT_ID)
                    it.executeUpdate()
                }
            },
            actionName = "리뷰 수정",
            waitTable = TABLE,
            action = { reviewCommandService.updateReview(reviews[0].id!!, BUYER_IDS[0], ReviewUpdateRequest(2, "평점을 바꿉니다 첫째")) }
        )

        assertThat(result.isSuccess).withFailMessage { result.exceptionOrNull().toString() }.isTrue()
        val stats = stats()
        assertThat(stats.totalSalesQuantity).isEqualTo(15L)
        assertThat(stats.orderCount).isEqualTo(1L)
        assertThat(stats.reviewCount).isEqualTo(3L)
        assertThat(stats.averageRating).isEqualByComparingTo("4.00")
    }

    private fun seedStatistics() {
        productStatisticsRepository.saveAndFlush(
            ProductStatistics.create(
                productId = PRODUCT_ID,
                productName = "리뷰 동시 갱신 상품",
                sellerId = 1L,
                categoryId = 1L,
                totalSalesQuantity = 10,
                totalSalesAmount = BigDecimal("100000")
            )
        )
        jdbcTemplate.update(
            "UPDATE product_statistics SET average_rating = 5.00, review_count = 3 WHERE product_id = ?",
            PRODUCT_ID
        )
    }

    private fun seedReviews(): List<Review> =
        BUYER_IDS.mapIndexed { index, buyerId ->
            reviewRepository.saveAndFlush(
                Review.create(
                    buyerId = buyerId,
                    orderItemId = ORDER_ITEM_BASE + index,
                    productId = PRODUCT_ID,
                    rating = 5,
                    content = "처음 남긴 리뷰 내용입니다 $index"
                )
            )
        }

    private fun stats(): ProductStatistics = productStatisticsRepository.findByProductId(PRODUCT_ID)!!

    private fun runConcurrentlyWhileStatisticsLocked(vararg actions: () -> Unit) {
        val results = dataSource.connection.use { holder ->
            holder.autoCommit = false
            try {
                holder.prepareStatement("SELECT id FROM product_statistics WHERE product_id = ? FOR UPDATE").use {
                    it.setLong(1, PRODUCT_ID)
                    it.executeQuery().close()
                }
                val pending = actions.map { action -> executor.submit<Result<Unit>> { runCatching { action() } } }
                awaitLockWaits(actions.size.toLong())
                holder.commit()
                pending.map { it.get(30, TimeUnit.SECONDS) }
            } finally {
                holder.rollback()
                holder.autoCommit = true
            }
        }
        results.forEach { assertThat(it.isSuccess).withFailMessage { it.exceptionOrNull().toString() }.isTrue() }
    }

    private fun awaitLockWaits(expected: Long) {
        val deadline = System.currentTimeMillis() + LOCK_WAIT_LIMIT_MS
        while (System.currentTimeMillis() < deadline) {
            if (lockWaitCount(TABLE) >= expected) return
            Thread.onSpinWait()
        }
        throw AssertionError("$TABLE 잠금 대기 ${expected}건이 ${LOCK_WAIT_LIMIT_MS}ms 안에 보이지 않았다: ${lockWaitCount(TABLE)}건")
    }

    companion object {
        private const val PRODUCT_ID = 7302L
        private const val ORDER_ITEM_BASE = 730_200L
        private const val TABLE = "product_statistics"
        private const val LOCK_WAIT_LIMIT_MS = 15_000L
        private val BUYER_IDS = listOf(73_021L, 73_022L, 73_023L)
    }
}
