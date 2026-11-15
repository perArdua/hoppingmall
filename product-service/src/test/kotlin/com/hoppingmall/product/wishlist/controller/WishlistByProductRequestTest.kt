package com.hoppingmall.product.wishlist.controller

import com.hoppingmall.product.category.dto.request.CategoryCreateRequest
import com.hoppingmall.product.category.service.CategoryCommandService
import com.hoppingmall.product.common.enums.ProductStatus
import com.hoppingmall.product.product.domain.Product
import com.hoppingmall.product.product.domain.repository.ProductRepository
import com.hoppingmall.product.support.TestCacheConfig
import com.hoppingmall.product.wishlist.domain.repository.WishlistRepository
import com.hoppingmall.product.wishlist.dto.request.WishlistCreateRequest
import com.hoppingmall.product.wishlist.service.WishlistCommandService
import org.assertj.core.api.Assertions.assertThat
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
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import java.math.BigDecimal
import java.util.UUID

@DisplayName("상품 id로 찜 해제·찜 확인 응답 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@SpringBootTest(properties = ["spring.main.allow-bean-definition-overriding=true"])
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestCacheConfig::class)
class WishlistByProductRequestTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var categoryCommandService: CategoryCommandService

    @Autowired
    private lateinit var productRepository: ProductRepository

    @Autowired
    private lateinit var wishlistCommandService: WishlistCommandService

    @Autowired
    private lateinit var wishlistRepository: WishlistRepository

    private var productId: Long = 0

    @BeforeEach
    fun setUp() {
        CLEANED_TABLES.forEach { jdbcTemplate.update("DELETE FROM $it") }
        val categoryId = categoryCommandService.createCategory(CategoryCreateRequest(name = "찜-상품id", parentCategoryId = null)).id
        productId = productRepository.saveAndFlush(
            Product.create(SELLER_ID, categoryId, "찜 해제 상품", "설명", BigDecimal("1000"), ProductStatus.AVAILABLE)
        ).id!!
    }

    @AfterEach
    fun tearDown() {
        CLEANED_TABLES.forEach { jdbcTemplate.update("DELETE FROM $it") }
    }

    @Test
    fun 상품_id로_찜을_해제하면_찜이_지워지고_찜_확인은_찜하지_않음이다() {
        wishlistCommandService.addWishlist(BUYER_ID, WishlistCreateRequest(productId))

        mockMvc.delete("/api/v1/wishlists/product/$productId") {
            buyer(BUYER_ID)
            header("Idempotency-Key", UUID.randomUUID().toString())
        }.andExpect {
            status { isOk() }
            jsonPath("$.code") { value("SUCCESS") }
        }

        assertThat(wishlistRepository.findAll()).isEmpty()
        mockMvc.get("/api/v1/wishlists/check/$productId") { buyer(BUYER_ID) }.andExpect {
            status { isOk() }
            jsonPath("$.data.wishlisted") { value(false) }
            jsonPath("$.data.wishlist_id") { doesNotExist() }
        }
    }

    @Test
    fun 찜하지_않은_상품을_상품_id로_해제하면_404다() {
        mockMvc.delete("/api/v1/wishlists/product/$productId") {
            buyer(BUYER_ID)
            header("Idempotency-Key", UUID.randomUUID().toString())
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("WISH001") }
        }
    }

    @Test
    fun 다른_구매자의_찜은_상품_id로_해제되지_않는다() {
        val wishlistId = wishlistCommandService.addWishlist(BUYER_ID, WishlistCreateRequest(productId)).id

        mockMvc.delete("/api/v1/wishlists/product/$productId") {
            buyer(OTHER_BUYER_ID)
            header("Idempotency-Key", UUID.randomUUID().toString())
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("WISH001") }
        }

        assertThat(wishlistRepository.findById(wishlistId)).isPresent
    }

    @Test
    fun 찜_확인_응답에_찜_여부와_찜_id를_준다() {
        val wishlistId = wishlistCommandService.addWishlist(BUYER_ID, WishlistCreateRequest(productId)).id

        mockMvc.get("/api/v1/wishlists/check/$productId") { buyer(BUYER_ID) }.andExpect {
            status { isOk() }
            jsonPath("$.data.wishlisted") { value(true) }
            jsonPath("$.data.wishlist_id") { value(wishlistId) }
        }
    }

    private fun org.springframework.test.web.servlet.MockHttpServletRequestDsl.buyer(buyerId: Long) {
        header("X-User-Id", buyerId.toString())
        header("x-user-role", "BUYER")
    }

    companion object {
        private const val SELLER_ID = 9_201L
        private const val BUYER_ID = 9_202L
        private const val OTHER_BUYER_ID = 9_203L
        private val CLEANED_TABLES = listOf("wishlists", "reviews", "product_statistics", "product_images", "products", "categories")
    }
}
