package com.hoppingmall.product.wishlist.controller

import com.hoppingmall.product.category.dto.request.CategoryCreateRequest
import com.hoppingmall.product.category.service.CategoryCommandService
import com.hoppingmall.product.common.enums.ProductStatus
import com.hoppingmall.product.product.dto.request.ProductCreateRequest
import com.hoppingmall.product.product.service.ProductCommandService
import com.hoppingmall.product.support.TestCacheConfig
import com.hoppingmall.product.wishlist.dto.request.WishlistCreateRequest
import com.hoppingmall.product.wishlist.service.WishlistCommandService
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

@DisplayName("찜 목록 정렬 파라미터 오류 응답 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@SpringBootTest(properties = ["spring.main.allow-bean-definition-overriding=true"])
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestCacheConfig::class)
class WishlistSortErrorResponseTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var categoryCommandService: CategoryCommandService

    @Autowired
    private lateinit var productCommandService: ProductCommandService

    @Autowired
    private lateinit var wishlistCommandService: WishlistCommandService

    @BeforeEach
    fun setUp() {
        CATALOG_TABLES.forEach { jdbcTemplate.update("DELETE FROM $it") }
    }

    @AfterEach
    fun tearDown() {
        CATALOG_TABLES.forEach { jdbcTemplate.update("DELETE FROM $it") }
    }

    @Test
    fun 찜_목록을_없는_속성으로_정렬하면_400을_응답한다() {
        mockMvc.get("/api/v1/wishlists") {
            param("sort", "foo")
            header("X-User-Id", BUYER_ID.toString())
            header("x-user-role", "BUYER")
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("C001") }
        }
    }

    @Test
    fun 찜_목록을_찜에_없는_상품_속성으로_정렬하면_400을_응답한다() {
        mockMvc.get("/api/v1/wishlists") {
            param("sort", "price,desc")
            header("X-User-Id", BUYER_ID.toString())
            header("x-user-role", "BUYER")
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("C001") }
        }
    }

    @Test
    fun 찜_목록을_찜의_속성으로_정렬하면_정렬된_목록을_응답한다() {
        val categoryId = categoryCommandService.createCategory(CategoryCreateRequest(name = "찜정렬", parentCategoryId = null)).id
        val first = createProduct(categoryId, "찜정렬 첫 상품")
        val second = createProduct(categoryId, "찜정렬 둘째 상품")
        wishlistCommandService.addWishlist(BUYER_ID, WishlistCreateRequest(first))
        wishlistCommandService.addWishlist(BUYER_ID, WishlistCreateRequest(second))

        mockMvc.get("/api/v1/wishlists") {
            param("sort", "productId,desc")
            header("X-User-Id", BUYER_ID.toString())
            header("x-user-role", "BUYER")
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.content[0].product_id") { value(second) }
            jsonPath("$.data.content[1].product_id") { value(first) }
        }
    }

    private fun createProduct(categoryId: Long, name: String): Long =
        productCommandService.createProduct(
            ProductCreateRequest(
                sellerId = SELLER_ID,
                categoryId = categoryId,
                name = name,
                description = "찜정렬 설명",
                price = BigDecimal("1000"),
                imageUrls = listOf("a.jpg"),
                status = ProductStatus.AVAILABLE
            )
        ).id

    companion object {
        private const val SELLER_ID = 9_421L
        private const val BUYER_ID = 9_422L
        private val CATALOG_TABLES = listOf("wishlists", "reviews", "product_statistics", "product_images", "products", "categories")
    }
}
