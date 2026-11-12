package com.hoppingmall.product.wishlist.service

import com.hoppingmall.product.category.dto.request.CategoryCreateRequest
import com.hoppingmall.product.category.service.CategoryCommandService
import com.hoppingmall.product.common.enums.ProductStatus
import com.hoppingmall.product.product.dto.request.ProductCreateRequest
import com.hoppingmall.product.product.service.ProductCommandService
import com.hoppingmall.product.support.MySqlIntegrationTestSupport
import com.hoppingmall.product.wishlist.dto.request.WishlistCreateRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.PageRequest
import java.math.BigDecimal

@DisplayName("삭제된 상품의 찜 목록 MySQL 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class WishlistDeletedProductMySqlIntegrationTest : MySqlIntegrationTestSupport() {

    @Autowired
    private lateinit var wishlistCommandService: WishlistCommandService

    @Autowired
    private lateinit var wishlistQueryService: WishlistQueryService

    @Autowired
    private lateinit var categoryCommandService: CategoryCommandService

    @Autowired
    private lateinit var productCommandService: ProductCommandService

    @BeforeEach
    fun cleanCatalog() {
        CATALOG_TABLES.forEach { jdbcTemplate.update("DELETE FROM $it") }
    }

    @AfterEach
    fun cleanCatalogAfter() {
        CATALOG_TABLES.forEach { jdbcTemplate.update("DELETE FROM $it") }
    }

    @Test
    fun 찜한_상품이_삭제되면_찜_목록에_나오지_않고_남은_상품의_찜만_나온다() {
        val categoryId = categoryCommandService.createCategory(CategoryCreateRequest(name = "찜목록-카테고리")).id
        val deletedProductId = createProduct(categoryId, "삭제될 상품")
        val keptProductId = createProduct(categoryId, "남는 상품")
        wishlistCommandService.addWishlist(BUYER_ID, WishlistCreateRequest(deletedProductId))
        val keptWishlistId = wishlistCommandService.addWishlist(BUYER_ID, WishlistCreateRequest(keptProductId)).id
        productCommandService.deleteProduct(deletedProductId)

        val wishlists = wishlistQueryService.getWishlists(BUYER_ID, PageRequest.of(0, 20)).content

        assertThat(wishlists.map { it.id }).containsExactly(keptWishlistId)
        assertThat(wishlists.single().productName).isEqualTo("남는 상품")
        assertThat(wishlists).noneMatch { it.productName == null }
    }

    private fun createProduct(categoryId: Long, name: String): Long =
        productCommandService.createProduct(
            ProductCreateRequest(
                sellerId = SELLER_ID,
                categoryId = categoryId,
                name = name,
                description = "찜목록 설명",
                price = BigDecimal("10000"),
                imageUrls = listOf("a.jpg"),
                status = ProductStatus.AVAILABLE
            )
        ).id

    companion object {
        private const val SELLER_ID = 8_421L
        private const val BUYER_ID = 8_422L
        private val CATALOG_TABLES = listOf("wishlists", "reviews", "product_statistics", "product_images", "products", "categories")
    }
}
