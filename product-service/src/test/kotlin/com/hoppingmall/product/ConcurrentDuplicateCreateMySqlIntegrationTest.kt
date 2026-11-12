package com.hoppingmall.product

import com.hoppingmall.common.BusinessException
import com.hoppingmall.product.category.dto.request.CategoryCreateRequest
import com.hoppingmall.product.category.service.CategoryCommandService
import com.hoppingmall.product.common.enums.ProductStatus
import com.hoppingmall.product.product.domain.repository.ProductStatisticsRepository
import com.hoppingmall.product.product.dto.request.ProductCreateRequest
import com.hoppingmall.product.product.service.ProductCommandService
import com.hoppingmall.product.review.domain.repository.ReviewRepository
import com.hoppingmall.product.review.dto.request.ReviewCreateRequest
import com.hoppingmall.product.review.port.OrderItemInfo
import com.hoppingmall.product.review.port.OrderQueryPort
import com.hoppingmall.product.review.service.ReviewCommandServiceImpl
import com.hoppingmall.product.support.MySqlIntegrationTestSupport
import com.hoppingmall.product.wishlist.dto.request.WishlistCreateRequest
import com.hoppingmall.product.wishlist.service.WishlistCommandService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal

@DisplayName("카테고리 생성·리뷰 작성·찜하기 동시 중복 MySQL 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class ConcurrentDuplicateCreateMySqlIntegrationTest : MySqlIntegrationTestSupport() {

    @Autowired
    private lateinit var categoryCommandService: CategoryCommandService

    @Autowired
    private lateinit var productCommandService: ProductCommandService

    @Autowired
    private lateinit var wishlistCommandService: WishlistCommandService

    @Autowired
    private lateinit var reviewRepository: ReviewRepository

    @Autowired
    private lateinit var productStatisticsRepository: ProductStatisticsRepository

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    @BeforeEach
    fun cleanCatalog() {
        CATALOG_TABLES.forEach { jdbcTemplate.update("DELETE FROM $it") }
    }

    @AfterEach
    fun cleanCatalogAfter() {
        CATALOG_TABLES.forEach { jdbcTemplate.update("DELETE FROM $it") }
    }

    @Test
    fun 같은_이름의_카테고리가_동시에_만들어지면_늦은_쪽은_409_오류를_받는다() {
        val result = runWhileHolding(
            holdingChanges = { connection ->
                connection.prepareStatement(
                    "INSERT INTO categories (name, parent_category_id, depth, created_at) VALUES (?, NULL, 0, NOW())"
                ).use {
                    it.setString(1, CATEGORY_NAME)
                    it.executeUpdate()
                }
            },
            actionName = "카테고리 생성",
            waitTable = "categories"
        ) {
            categoryCommandService.createCategory(CategoryCreateRequest(name = CATEGORY_NAME))
        }

        assertConflict(result, "CAT002")
        assertThat(count("SELECT COUNT(*) FROM categories WHERE name = ?", CATEGORY_NAME)).isEqualTo(1L)
    }

    @Test
    fun 같은_주문_항목의_리뷰가_동시에_작성되면_늦은_쪽은_409_오류를_받는다() {
        val orderQueryPort = mock<OrderQueryPort>()
        whenever(orderQueryPort.findOrderItemById(ORDER_ITEM_ID)).thenReturn(
            OrderItemInfo(ORDER_ITEM_ID, ORDER_ID, SELLER_ID, PRODUCT_ID, "동시 리뷰 상품", BigDecimal("10000"), 1, BigDecimal("10000"))
        )
        whenever(orderQueryPort.isDelivered(any(), any())).thenReturn(true)
        val service = ReviewCommandServiceImpl(reviewRepository, orderQueryPort, productStatisticsRepository)
        val tx = TransactionTemplate(transactionManager)

        val result = runWhileHolding(
            holdingChanges = { connection ->
                connection.prepareStatement(
                    "INSERT INTO reviews (buyer_id, order_item_id, product_id, rating, content, created_at) VALUES (?, ?, ?, 5, ?, NOW())"
                ).use {
                    it.setLong(1, BUYER_ID)
                    it.setLong(2, ORDER_ITEM_ID)
                    it.setLong(3, PRODUCT_ID)
                    it.setString(4, "먼저 들어온 리뷰 내용입니다")
                    it.executeUpdate()
                }
            },
            actionName = "리뷰 작성",
            waitTable = "reviews"
        ) {
            tx.execute {
                service.createReview(BUYER_ID, ReviewCreateRequest(orderItemId = ORDER_ITEM_ID, rating = 3, content = "늦게 들어온 리뷰 내용입니다"))
            }
        }

        assertConflict(result, "REV002")
        assertThat(count("SELECT COUNT(*) FROM reviews WHERE order_item_id = ?", ORDER_ITEM_ID)).isEqualTo(1L)
    }

    @Test
    fun 같은_상품을_동시에_찜하면_늦은_쪽은_409_오류를_받는다() {
        val categoryId = categoryCommandService.createCategory(CategoryCreateRequest(name = "동시찜-카테고리")).id
        val productId = productCommandService.createProduct(
            ProductCreateRequest(
                sellerId = SELLER_ID,
                categoryId = categoryId,
                name = "동시찜 상품",
                description = "동시찜 설명",
                price = BigDecimal("10000"),
                imageUrls = listOf("a.jpg"),
                status = ProductStatus.AVAILABLE
            )
        ).id

        val result = runWhileHolding(
            holdingChanges = { connection ->
                connection.prepareStatement(
                    "INSERT INTO wishlists (buyer_id, product_id, created_at) VALUES (?, ?, NOW())"
                ).use {
                    it.setLong(1, BUYER_ID)
                    it.setLong(2, productId)
                    it.executeUpdate()
                }
            },
            actionName = "찜하기",
            waitTable = "wishlists"
        ) {
            wishlistCommandService.addWishlist(BUYER_ID, WishlistCreateRequest(productId))
        }

        assertConflict(result, "WISH002")
        assertThat(count("SELECT COUNT(*) FROM wishlists WHERE buyer_id = ? AND product_id = ?", BUYER_ID, productId)).isEqualTo(1L)
    }

    private fun assertConflict(result: Result<Unit>, code: String) {
        val thrown = result.exceptionOrNull()
        assertThat(thrown).isInstanceOf(BusinessException::class.java)
        val errorCode = (thrown as BusinessException).errorCode
        assertThat(errorCode.status).isEqualTo(HttpStatus.CONFLICT)
        assertThat(errorCode.code).isEqualTo(code)
    }

    private fun count(sql: String, vararg args: Any): Long =
        jdbcTemplate.queryForObject(sql, Long::class.java, *args)!!

    companion object {
        private const val CATEGORY_NAME = "동시생성-카테고리"
        private const val SELLER_ID = 8_401L
        private const val BUYER_ID = 8_402L
        private const val PRODUCT_ID = 8_403L
        private const val ORDER_ID = 8_404L
        private const val ORDER_ITEM_ID = 8_405L
        private val CATALOG_TABLES = listOf("wishlists", "reviews", "product_statistics", "product_images", "products", "categories")
    }
}
