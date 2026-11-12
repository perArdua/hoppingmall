package com.hoppingmall.product.product.controller

import com.hoppingmall.product.category.dto.request.CategoryCreateRequest
import com.hoppingmall.product.category.service.CategoryCommandService
import com.hoppingmall.product.common.enums.ProductStatus
import com.hoppingmall.product.product.dto.request.ProductCreateRequest
import com.hoppingmall.product.product.service.ProductCommandService
import com.hoppingmall.product.support.TestCacheConfig
import org.hamcrest.Matchers.containsInAnyOrder
import org.hamcrest.Matchers.hasSize
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

@DisplayName("H2 프로필 상품 키워드 검색 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@SpringBootTest(properties = ["spring.main.allow-bean-definition-overriding=true"])
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestCacheConfig::class)
class ProductKeywordSearchH2ResponseTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var categoryCommandService: CategoryCommandService

    @Autowired
    private lateinit var productCommandService: ProductCommandService

    @BeforeEach
    fun setUp() {
        CATALOG_TABLES.forEach { jdbcTemplate.update("DELETE FROM $it") }
    }

    @AfterEach
    fun tearDown() {
        CATALOG_TABLES.forEach { jdbcTemplate.update("DELETE FROM $it") }
    }

    @Test
    fun 키워드로_검색하면_오류_없이_이름이나_설명이_맞는_상품만_응답한다() {
        val categoryId = categoryCommandService.createCategory(CategoryCreateRequest(name = "검색-H2", parentCategoryId = null)).id
        val byName = createProduct(categoryId, "가벼운 운동화", "매일 신는 신발")
        val byDescription = createProduct(categoryId, "러닝화", "쿠션 좋은 운동화")
        createProduct(categoryId, "겨울 패딩", "따뜻한 외투")

        mockMvc.get("/api/v1/products/search") {
            param("keyword", "운동화")
            header("X-User-Id", "1")
            header("x-user-role", "BUYER")
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.content", hasSize<Any>(2))
            jsonPath("$.data.content[*].id", containsInAnyOrder(byName.toInt(), byDescription.toInt()))
        }
    }

    private fun createProduct(categoryId: Long, name: String, description: String): Long =
        productCommandService.createProduct(
            ProductCreateRequest(
                sellerId = SELLER_ID,
                categoryId = categoryId,
                name = name,
                description = description,
                price = BigDecimal("1000"),
                imageUrls = listOf("a.jpg"),
                status = ProductStatus.AVAILABLE
            )
        ).id

    companion object {
        private const val SELLER_ID = 9_431L
        private val CATALOG_TABLES = listOf("wishlists", "reviews", "product_statistics", "product_images", "products", "categories")
    }
}
