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

@DisplayName("삭제된 상품의 찜 여부 조회 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@SpringBootTest(properties = ["spring.main.allow-bean-definition-overriding=true"])
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestCacheConfig::class)
class WishlistCheckDeletedProductTest {

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
    fun 찜한_상품이_삭제되면_찜_여부는_false다() {
        val productId = createProduct("찜여부 삭제 상품")
        wishlistCommandService.addWishlist(BUYER_ID, WishlistCreateRequest(productId))
        productCommandService.deleteProduct(productId)

        mockMvc.get("/api/v1/wishlists/check/$productId") {
            header("X-User-Id", BUYER_ID.toString())
            header("x-user-role", "BUYER")
        }.andExpect {
            status { isOk() }
            jsonPath("$.data") { value(false) }
        }
    }

    @Test
    fun 찜한_상품이_삭제되면_찜_목록과_찜_여부가_같은_답을_준다() {
        val productId = createProduct("찜목록 삭제 상품")
        wishlistCommandService.addWishlist(BUYER_ID, WishlistCreateRequest(productId))
        productCommandService.deleteProduct(productId)

        mockMvc.get("/api/v1/wishlists") {
            header("X-User-Id", BUYER_ID.toString())
            header("x-user-role", "BUYER")
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.content.length()") { value(0) }
        }
        mockMvc.get("/api/v1/wishlists/check/$productId") {
            header("X-User-Id", BUYER_ID.toString())
            header("x-user-role", "BUYER")
        }.andExpect {
            status { isOk() }
            jsonPath("$.data") { value(false) }
        }
    }

    @Test
    fun 찜한_상품이_남아_있으면_찜_여부는_true다() {
        val productId = createProduct("찜여부 남은 상품")
        wishlistCommandService.addWishlist(BUYER_ID, WishlistCreateRequest(productId))

        mockMvc.get("/api/v1/wishlists/check/$productId") {
            header("X-User-Id", BUYER_ID.toString())
            header("x-user-role", "BUYER")
        }.andExpect {
            status { isOk() }
            jsonPath("$.data") { value(true) }
        }
    }

    private fun createProduct(name: String): Long {
        val categoryId = categoryCommandService.createCategory(CategoryCreateRequest(name = "$name 카테고리", parentCategoryId = null)).id
        return productCommandService.createProduct(
            ProductCreateRequest(
                sellerId = SELLER_ID,
                categoryId = categoryId,
                name = name,
                description = "찜여부 설명",
                price = BigDecimal("1000"),
                imageUrls = listOf("a.jpg"),
                status = ProductStatus.AVAILABLE
            )
        ).id
    }

    companion object {
        private const val SELLER_ID = 9_861L
        private const val BUYER_ID = 9_862L
        private val CATALOG_TABLES = listOf("wishlists", "reviews", "product_statistics", "product_images", "products", "categories")
    }
}
