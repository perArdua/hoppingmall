package com.hoppingmall.product.category.service

import com.hoppingmall.common.BusinessException
import com.hoppingmall.product.category.domain.repository.CategoryRepository
import com.hoppingmall.product.category.dto.request.CategoryCreateRequest
import com.hoppingmall.product.common.enums.ProductStatus
import com.hoppingmall.product.product.dto.request.ProductCreateRequest
import com.hoppingmall.product.product.service.ProductCommandService
import com.hoppingmall.product.support.MySqlIntegrationTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import java.math.BigDecimal

@DisplayName("상품이 남은 카테고리 삭제 MySQL 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class CategoryDeleteWithProductsMySqlIntegrationTest : MySqlIntegrationTestSupport() {

    @Autowired
    private lateinit var categoryCommandService: CategoryCommandService

    @Autowired
    private lateinit var categoryRepository: CategoryRepository

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
    fun 상품이_남아_있는_카테고리는_삭제를_409_오류로_거절하고_그대로_남는다() {
        val categoryId = createCategory("상품남은-카테고리")
        createProduct(categoryId)

        val thrown = catchThrowable { categoryCommandService.deleteCategory(categoryId) }

        assertThat(thrown).isInstanceOf(BusinessException::class.java)
        val errorCode = (thrown as BusinessException).errorCode
        assertThat(errorCode.status).isEqualTo(HttpStatus.CONFLICT)
        assertThat(errorCode.code).isEqualTo("CAT005")
        assertThat(categoryRepository.findNullableById(categoryId)).isNotNull()
    }

    @Test
    fun 상품이_모두_삭제된_카테고리는_삭제할_수_있다() {
        val categoryId = createCategory("상품삭제된-카테고리")
        val productId = createProduct(categoryId)
        productCommandService.deleteProduct(productId)

        categoryCommandService.deleteCategory(categoryId)

        assertThat(categoryRepository.findNullableById(categoryId)).isNull()
    }

    @Test
    fun 다른_카테고리에만_상품이_있으면_삭제할_수_있다() {
        val emptyId = createCategory("빈-카테고리")
        createProduct(createCategory("상품있는-카테고리"))

        categoryCommandService.deleteCategory(emptyId)

        assertThat(categoryRepository.findNullableById(emptyId)).isNull()
    }

    private fun createCategory(name: String): Long =
        categoryCommandService.createCategory(CategoryCreateRequest(name = name)).id

    private fun createProduct(categoryId: Long): Long =
        productCommandService.createProduct(
            ProductCreateRequest(
                sellerId = SELLER_ID,
                categoryId = categoryId,
                name = "카테고리 삭제 상품",
                description = "카테고리 삭제 설명",
                price = BigDecimal("10000"),
                imageUrls = listOf("a.jpg"),
                status = ProductStatus.AVAILABLE
            )
        ).id

    companion object {
        private const val SELLER_ID = 8_411L
        private val CATALOG_TABLES = listOf("wishlists", "reviews", "product_statistics", "product_images", "products", "categories")
    }
}
