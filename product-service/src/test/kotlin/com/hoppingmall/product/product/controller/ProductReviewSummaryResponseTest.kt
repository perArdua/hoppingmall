package com.hoppingmall.product.product.controller

import com.hoppingmall.product.category.dto.request.CategoryCreateRequest
import com.hoppingmall.product.category.dto.request.CategoryUpdateRequest
import com.hoppingmall.product.category.service.CategoryCommandService
import com.hoppingmall.product.common.enums.ProductStatus
import com.hoppingmall.product.product.domain.Product
import com.hoppingmall.product.product.domain.repository.ProductRepository
import com.hoppingmall.product.review.domain.Review
import com.hoppingmall.product.review.domain.repository.ReviewRepository
import com.hoppingmall.product.review.dto.request.ReviewUpdateRequest
import com.hoppingmall.product.review.service.ReviewCommandService
import com.hoppingmall.product.support.TestCacheConfig
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.math.BigDecimal

@DisplayName("상품 응답의 평점 평균·리뷰 수·카테고리 이름 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@SpringBootTest(properties = ["spring.main.allow-bean-definition-overriding=true"])
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestCacheConfig::class)
class ProductReviewSummaryResponseTest {

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

    @Autowired
    private lateinit var reviewCommandService: ReviewCommandService

    private var categoryId: Long = 0
    private var productId: Long = 0
    private lateinit var reviews: List<Review>

    @BeforeEach
    fun setUp() {
        CLEANED_TABLES.forEach { jdbcTemplate.update("DELETE FROM $it") }
        categoryId = categoryCommandService.createCategory(CategoryCreateRequest(name = CATEGORY_NAME, parentCategoryId = null)).id
        productId = productRepository.saveAndFlush(
            Product.create(SELLER_ID, categoryId, "평점 응답 상품", "평점 설명", BigDecimal("1000"), ProductStatus.AVAILABLE)
        ).id!!
        reviews = listOf(4, 5).mapIndexed { index, rating ->
            reviewRepository.saveAndFlush(
                Review.create(
                    buyerId = BUYER_ID + index,
                    orderItemId = 9_300L + index,
                    productId = productId,
                    rating = rating,
                    content = "평점 응답 리뷰 내용 $index"
                )
            )
        }
        reviewCommandService.updateReview(reviews[0].id!!, BUYER_ID, ReviewUpdateRequest(3, "평점을 바꾼 리뷰 내용"))
    }

    @AfterEach
    fun tearDown() {
        CLEANED_TABLES.forEach { jdbcTemplate.update("DELETE FROM $it") }
    }

    @Test
    fun 상품_상세에_평점_평균_리뷰_수_카테고리_이름을_준다() {
        mockMvc.get("/api/v1/products/$productId") { buyer() }.andExpect {
            status { isOk() }
            jsonPath("$.data.category_id") { value(categoryId) }
            jsonPath("$.data.category_name") { value(CATEGORY_NAME) }
            jsonPath("$.data.average_rating") { value(4.0) }
            jsonPath("$.data.review_count") { value(2) }
        }
    }

    @Test
    fun 상품_목록_판매자별_카테고리별_검색에_평점_평균_리뷰_수_카테고리_이름을_준다() {
        listOf(
            "/api/v1/products",
            "/api/v1/products/seller/$SELLER_ID",
            "/api/v1/products/category/$categoryId",
            "/api/v1/products/search?keyword=평점"
        ).forEach { path ->
            mockMvc.get(path) { buyer() }.andExpect {
                status { isOk() }
                jsonPath("$.data.content[0].id") { value(productId) }
                jsonPath("$.data.content[0].category_name") { value(CATEGORY_NAME) }
                jsonPath("$.data.content[0].average_rating") { value(4.0) }
                jsonPath("$.data.content[0].review_count") { value(2) }
            }
        }
    }

    @Test
    fun 리뷰를_지우면_캐시된_상품_상세도_새_집계를_준다() {
        mockMvc.get("/api/v1/products/$productId") { buyer() }.andExpect { jsonPath("$.data.review_count") { value(2) } }

        reviewCommandService.deleteReview(reviews[0].id!!, BUYER_ID)

        mockMvc.get("/api/v1/products/$productId") { buyer() }.andExpect {
            status { isOk() }
            jsonPath("$.data.average_rating") { value(5.0) }
            jsonPath("$.data.review_count") { value(1) }
        }
    }

    @Test
    fun 카테고리_이름을_바꾸면_캐시된_상품_상세도_새_이름을_준다() {
        mockMvc.get("/api/v1/products/$productId") { buyer() }.andExpect { jsonPath("$.data.category_name") { value(CATEGORY_NAME) } }

        categoryCommandService.updateCategory(categoryId, CategoryUpdateRequest(RENAMED_CATEGORY_NAME))

        mockMvc.get("/api/v1/products/$productId") { buyer() }.andExpect {
            status { isOk() }
            jsonPath("$.data.category_name") { value(RENAMED_CATEGORY_NAME) }
        }
    }

    private fun org.springframework.test.web.servlet.MockHttpServletRequestDsl.buyer() {
        header("X-User-Id", BUYER_ID.toString())
        header("x-user-role", "BUYER")
    }

    companion object {
        private const val SELLER_ID = 9_301L
        private const val BUYER_ID = 9_302L
        private const val CATEGORY_NAME = "평점응답-카테고리"
        private const val RENAMED_CATEGORY_NAME = "평점응답-바뀐-카테고리"
        private val CLEANED_TABLES = listOf("wishlists", "reviews", "product_statistics", "product_images", "products", "categories")
    }
}
