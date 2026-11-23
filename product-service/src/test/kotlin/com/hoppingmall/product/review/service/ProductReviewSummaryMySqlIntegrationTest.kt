package com.hoppingmall.product.review.service

import com.hoppingmall.product.category.dto.request.CategoryCreateRequest
import com.hoppingmall.product.category.service.CategoryCommandService
import com.hoppingmall.product.common.enums.ProductStatus
import com.hoppingmall.product.product.domain.Product
import com.hoppingmall.product.product.domain.repository.ProductRepository
import com.hoppingmall.product.product.dto.request.ProductUpdateRequest
import com.hoppingmall.product.product.service.ProductCommandService
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

@DisplayName("상품의 리뷰 집계(평점 평균·리뷰 수) MySQL 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class ProductReviewSummaryMySqlIntegrationTest : MySqlIntegrationTestSupport() {

    @Autowired
    private lateinit var reviewCommandService: ReviewCommandService

    @Autowired
    private lateinit var productReviewSummaryService: ProductReviewSummaryService

    @Autowired
    private lateinit var productCommandService: ProductCommandService

    @Autowired
    private lateinit var categoryCommandService: CategoryCommandService

    @Autowired
    private lateinit var productRepository: ProductRepository

    @Autowired
    private lateinit var reviewRepository: ReviewRepository

    private var categoryId: Long = 0
    private var productId: Long = 0

    @BeforeEach
    fun seedProduct() {
        cleanUp()
        categoryId = categoryCommandService.createCategory(CategoryCreateRequest(name = CATEGORY_NAME, parentCategoryId = null)).id
        productId = productRepository.saveAndFlush(
            Product.create(SELLER_ID, categoryId, "리뷰 집계 상품", "설명", BigDecimal("1000"), ProductStatus.AVAILABLE)
        ).id!!
    }

    @AfterEach
    fun cleanUp() {
        jdbcTemplate.update("DELETE FROM reviews WHERE order_item_id >= ? AND order_item_id < ?", ORDER_ITEM_BASE, ORDER_ITEM_BASE + 100)
        jdbcTemplate.update("DELETE FROM product_images WHERE product_id IN (SELECT id FROM products WHERE seller_id = ?)", SELLER_ID)
        jdbcTemplate.update("DELETE FROM products WHERE seller_id = ?", SELLER_ID)
        jdbcTemplate.update("DELETE FROM categories WHERE name = ?", CATEGORY_NAME)
    }

    @Test
    fun 같은_상품의_리뷰_수정_두_건이_동시에_오면_상품의_평점_평균과_리뷰_수에_둘_다_반영한다() {
        val reviews = seedReviews(productId)
        setSummary(productId, 3, "5.00")

        runConcurrentlyWhileProductLocked(
            { reviewCommandService.updateReview(reviews[0].id!!, BUYER_IDS[0], ReviewUpdateRequest(1, "평점을 낮춥니다 첫째")) },
            { reviewCommandService.updateReview(reviews[1].id!!, BUYER_IDS[1], ReviewUpdateRequest(1, "평점을 낮춥니다 둘째")) }
        )

        assertSummary(productId, 3, "2.33")
    }

    @Test
    fun 같은_상품의_리뷰_삭제_두_건이_동시에_오면_상품의_리뷰_수에_둘_다_반영한다() {
        val reviews = seedReviews(productId)
        setSummary(productId, 3, "5.00")

        runConcurrentlyWhileProductLocked(
            { reviewCommandService.deleteReview(reviews[0].id!!, BUYER_IDS[0]) },
            { reviewCommandService.deleteReview(reviews[1].id!!, BUYER_IDS[1]) }
        )

        assertSummary(productId, 1, "5.00")
    }

    @Test
    fun 판매자의_상품_수정이_그사이_커밋된_리뷰_집계를_덮어쓰지_않는다() {
        setSummary(productId, 3, "5.00")

        val result = runWhileHolding(
            holdingChanges = { holder ->
                holder.prepareStatement("UPDATE products SET review_count = 4, average_rating = 4.50 WHERE id = ?").use {
                    it.setLong(1, productId)
                    it.executeUpdate()
                }
            },
            actionName = "상품 수정",
            waitTable = TABLE,
            action = {
                productCommandService.updateProduct(
                    productId,
                    ProductUpdateRequest(categoryId = categoryId, name = "바뀐 이름", description = "바뀐 설명", price = BigDecimal("2000"))
                )
            }
        )

        assertThat(result.isSuccess).withFailMessage { result.exceptionOrNull().toString() }.isTrue()
        assertSummary(productId, 4, "4.50")
        assertThat(productRepository.findNullableById(productId)!!.name).isEqualTo("바뀐 이름")
    }

    @Test
    fun 집계_채우기가_기존_리뷰로_상품의_평점_평균과_리뷰_수를_채운다() {
        seedReviews(productId)
        reviewRepository.saveAndFlush(
            Review.create(buyerId = 73_031L, orderItemId = ORDER_ITEM_BASE + 10, productId = productId, rating = 2, content = "낮은 평점 리뷰 내용입니다")
        )
        val noReviewProductId = productRepository.saveAndFlush(
            Product.create(SELLER_ID, categoryId, "리뷰 없는 상품", "설명", BigDecimal("1000"), ProductStatus.AVAILABLE)
        ).id!!

        val result = productReviewSummaryService.backfill()

        assertThat(result.failed).isZero()
        assertThat(result.refreshed).isGreaterThanOrEqualTo(2)
        assertSummary(productId, 4, "4.25")
        assertSummary(noReviewProductId, 0, "0.00")
    }

    private fun seedReviews(productId: Long): List<Review> =
        BUYER_IDS.mapIndexed { index, buyerId ->
            reviewRepository.saveAndFlush(
                Review.create(
                    buyerId = buyerId,
                    orderItemId = ORDER_ITEM_BASE + index,
                    productId = productId,
                    rating = 5,
                    content = "처음 남긴 리뷰 내용입니다 $index"
                )
            )
        }

    private fun setSummary(productId: Long, reviewCount: Long, averageRating: String) {
        jdbcTemplate.update(
            "UPDATE products SET review_count = ?, average_rating = ? WHERE id = ?",
            reviewCount, BigDecimal(averageRating), productId
        )
    }

    private fun assertSummary(productId: Long, reviewCount: Long, averageRating: String) {
        val row = jdbcTemplate.queryForMap("SELECT review_count, average_rating FROM products WHERE id = ?", productId)
        assertThat((row["review_count"] as Number).toLong()).isEqualTo(reviewCount)
        assertThat(row["average_rating"] as BigDecimal).isEqualByComparingTo(averageRating)
    }

    private fun runConcurrentlyWhileProductLocked(vararg actions: () -> Unit) {
        val results = dataSource.connection.use { holder ->
            holder.autoCommit = false
            try {
                holder.prepareStatement("SELECT id FROM products WHERE id = ? FOR UPDATE").use {
                    it.setLong(1, productId)
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
        private const val SELLER_ID = 7_401L
        private const val ORDER_ITEM_BASE = 740_100L
        private const val TABLE = "products"
        private const val CATEGORY_NAME = "리뷰집계-MySQL"
        private const val LOCK_WAIT_LIMIT_MS = 15_000L
        private val BUYER_IDS = listOf(74_011L, 74_012L, 74_013L)
    }
}
