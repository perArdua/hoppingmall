package com.hoppingmall.product.config

import com.hoppingmall.product.category.dto.request.CategoryCreateRequest
import com.hoppingmall.product.category.service.CategoryCommandService
import com.hoppingmall.product.common.enums.ProductStatus
import com.hoppingmall.product.product.domain.Product
import com.hoppingmall.product.product.domain.repository.ProductRepository
import com.hoppingmall.product.review.domain.Review
import com.hoppingmall.product.review.domain.repository.ReviewRepository
import com.hoppingmall.product.support.TestCacheConfig
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.math.BigDecimal

@DisplayName("방문자(신원 없음) 조회 권한")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@SpringBootTest(properties = ["spring.main.allow-bean-definition-overriding=true"])
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestCacheConfig::class)
class VisitorReadAccessTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var categoryCommandService: CategoryCommandService

    @Autowired
    private lateinit var productRepository: ProductRepository

    @Autowired
    private lateinit var reviewRepository: ReviewRepository

    private var categoryId: Long = 0
    private var productId: Long = 0
    private var reviewId: Long = 0

    @BeforeEach
    fun setUp() {
        CLEANED_TABLES.forEach { jdbcTemplate.update("DELETE FROM $it") }
        categoryId = categoryCommandService.createCategory(CategoryCreateRequest(name = "방문자-조회", parentCategoryId = null)).id
        productId = productRepository.saveAndFlush(
            Product.create(SELLER_ID, categoryId, "방문자 상품", "설명", BigDecimal("1000"), ProductStatus.AVAILABLE)
        ).id!!
        reviewId = reviewRepository.saveAndFlush(
            Review.create(buyerId = BUYER_ID, orderItemId = 9_100L, productId = productId, rating = 5, content = "방문자도 보는 리뷰 내용")
        ).id!!
    }

    @AfterEach
    fun tearDown() {
        CLEANED_TABLES.forEach { jdbcTemplate.update("DELETE FROM $it") }
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "/api/v1/products",
            "/api/v1/products/{productId}",
            "/api/v1/products/seller/{sellerId}",
            "/api/v1/products/category/{categoryId}",
            "/api/v1/products/search?keyword=방문자",
            "/api/v1/products/{productId}/reviews",
            "/api/v1/reviews/{reviewId}",
            "/api/v1/categories/root",
            "/api/v1/categories/{categoryId}",
            "/api/v1/categories/{categoryId}/sub"
        ]
    )
    fun 신원_없이_상품_카테고리_리뷰를_조회하면_200이다(path: String) {
        mockMvc.get(resolve(path)).andExpect {
            status { isOk() }
            jsonPath("$.code") { value("SUCCESS") }
        }
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "/api/v1/wishlists",
            "/api/v1/wishlists/check/{productId}",
            "/api/v1/reviews/my",
            "/api/v1/products/bulk/1",
            "/api/v1/products/bulk/1/errors",
            "/api/v1/seller/dashboard/overview",
            "/api/v1/seller/dashboard/products/{productId}",
            "/api/v1/seller/dashboard/today",
            "/api/v1/seller/dashboard/daily",
            "/api/v1/seller/dashboard/hourly",
            "/api/v1/inventories/{productId}",
            "/api/v1/admin/product-statistics",
            "/api/v1/admin/dlq/stats",
            "/api/v1/admin/dlq/messages"
        ]
    )
    fun 신원이_필요한_조회를_신원_없이_하면_401과_인증_필요_코드다(path: String) {
        mockMvc.get(resolve(path)).andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("A001") }
        }
    }

    @Test
    fun 신원이_있으면_내_리뷰와_찜_목록을_조회한다() {
        mockMvc.get("/api/v1/reviews/my") {
            header("X-User-Id", BUYER_ID.toString())
            header("x-user-role", "BUYER")
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.content[0].id") { value(reviewId) }
        }

        mockMvc.get("/api/v1/wishlists") {
            header("X-User-Id", BUYER_ID.toString())
            header("x-user-role", "BUYER")
        }.andExpect { status { isOk() } }
    }

    @Test
    fun 신원이_있어도_상품_상세를_조회한다() {
        mockMvc.get("/api/v1/products/$productId") {
            header("X-User-Id", BUYER_ID.toString())
            header("x-user-role", "BUYER")
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.id") { value(productId) }
        }
    }

    @Test
    fun 신원이_있어도_권한이_없는_조회는_403이다() {
        mockMvc.get("/api/v1/admin/product-statistics") {
            header("X-User-Id", BUYER_ID.toString())
            header("x-user-role", "BUYER")
        }.andExpect { status { isForbidden() } }
    }

    private fun resolve(path: String): String = path
        .replace("{productId}", productId.toString())
        .replace("{sellerId}", SELLER_ID.toString())
        .replace("{categoryId}", categoryId.toString())
        .replace("{reviewId}", reviewId.toString())

    companion object {
        private const val SELLER_ID = 9_101L
        private const val BUYER_ID = 9_102L
        private val CLEANED_TABLES = listOf("reviews", "wishlists", "product_images", "products", "categories")
    }
}
