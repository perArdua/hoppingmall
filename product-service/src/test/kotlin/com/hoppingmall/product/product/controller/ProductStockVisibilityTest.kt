package com.hoppingmall.product.product.controller

import com.hoppingmall.product.category.dto.request.CategoryCreateRequest
import com.hoppingmall.product.category.service.CategoryCommandService
import com.hoppingmall.product.common.enums.ProductStatus
import com.hoppingmall.product.inventory.dto.request.InventoryInitRequest
import com.hoppingmall.product.inventory.service.InventoryCommandService
import com.hoppingmall.product.product.domain.Product
import com.hoppingmall.product.product.domain.repository.ProductRepository
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

@DisplayName("상품 응답의 재고 여부와 재고 수량 노출")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@SpringBootTest(properties = ["spring.main.allow-bean-definition-overriding=true"])
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestCacheConfig::class)
class ProductStockVisibilityTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var categoryCommandService: CategoryCommandService

    @Autowired
    private lateinit var productRepository: ProductRepository

    @Autowired
    private lateinit var inventoryCommandService: InventoryCommandService

    private var productId: Long = 0

    @BeforeEach
    fun setUp() {
        CLEANED_TABLES.forEach { jdbcTemplate.update("DELETE FROM $it") }
        val categoryId = categoryCommandService.createCategory(CategoryCreateRequest(name = "재고노출", parentCategoryId = null)).id
        productId = productRepository.saveAndFlush(
            Product.create(SELLER, categoryId, "재고 노출 상품", "설명", BigDecimal("1000"), ProductStatus.AVAILABLE)
        ).id!!
        inventoryCommandService.initStock(InventoryInitRequest(productId = productId, stockQuantity = 3))
    }

    @AfterEach
    fun tearDown() {
        CLEANED_TABLES.forEach { jdbcTemplate.update("DELETE FROM $it") }
    }

    @Test
    fun 방문자의_상품_상세에는_재고_여부만_있고_재고_수량은_없다() {
        mockMvc.get("/api/v1/products/$productId").andExpect {
            status { isOk() }
            jsonPath("$.data.in_stock") { value(true) }
            jsonPath("$.data.stock_quantity") { doesNotExist() }
        }
    }

    @Test
    fun 방문자의_상품_목록에는_재고_여부만_있고_재고_수량은_없다() {
        mockMvc.get("/api/v1/products").andExpect {
            status { isOk() }
            jsonPath("$.data.content[0].in_stock") { value(true) }
            jsonPath("$.data.content[0].stock_quantity") { doesNotExist() }
        }
    }

    @Test
    fun 방문자의_판매자_상품_목록에는_재고_수량이_없다() {
        mockMvc.get("/api/v1/products/seller/$SELLER").andExpect {
            status { isOk() }
            jsonPath("$.data.content[0].in_stock") { value(true) }
            jsonPath("$.data.content[0].stock_quantity") { doesNotExist() }
        }
    }

    @Test
    fun 판매자_본인의_상품_목록에는_재고_수량이_있다() {
        mockMvc.get("/api/v1/products/seller/$SELLER") {
            header("X-User-Id", SELLER.toString())
            header("x-user-role", "SELLER")
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.content[0].stock_quantity") { value(3) }
            jsonPath("$.data.content[0].in_stock") { value(true) }
        }
    }

    @Test
    fun 다른_판매자에게는_재고_수량을_주지_않는다() {
        mockMvc.get("/api/v1/products/seller/$SELLER") {
            header("X-User-Id", OTHER_SELLER.toString())
            header("x-user-role", "SELLER")
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.content[0].stock_quantity") { doesNotExist() }
        }
    }

    @Test
    fun 관리자에게는_판매자_상품_목록의_재고_수량을_준다() {
        mockMvc.get("/api/v1/products/seller/$SELLER") {
            header("X-User-Id", ADMIN.toString())
            header("x-user-role", "ADMIN")
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.content[0].stock_quantity") { value(3) }
        }
    }

    companion object {
        private const val SELLER = 7_501L
        private const val OTHER_SELLER = 7_502L
        private const val ADMIN = 7_503L
        private val CLEANED_TABLES = listOf(
            "inventory_reservations", "inventory_buckets", "inventories",
            "wishlists", "reviews", "product_statistics", "product_images", "products", "categories"
        )
    }
}
