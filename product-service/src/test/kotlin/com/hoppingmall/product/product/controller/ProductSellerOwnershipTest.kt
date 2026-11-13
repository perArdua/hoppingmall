package com.hoppingmall.product.product.controller

import com.hoppingmall.product.category.dto.request.CategoryCreateRequest
import com.hoppingmall.product.category.service.CategoryCommandService
import com.hoppingmall.product.common.enums.ProductStatus
import com.hoppingmall.product.inventory.domain.repository.InventoryRepository
import com.hoppingmall.product.inventory.dto.request.InventoryInitRequest
import com.hoppingmall.product.inventory.service.InventoryCommandService
import com.hoppingmall.product.product.domain.Product
import com.hoppingmall.product.product.domain.repository.ProductRepository
import com.hoppingmall.product.support.TestCacheConfig
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
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import java.math.BigDecimal

@DisplayName("상품·재고 변경의 판매자 소유 확인")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@SpringBootTest(properties = ["spring.main.allow-bean-definition-overriding=true"])
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestCacheConfig::class)
class ProductSellerOwnershipTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var categoryCommandService: CategoryCommandService

    @Autowired
    private lateinit var productRepository: ProductRepository

    @Autowired
    private lateinit var inventoryRepository: InventoryRepository

    @Autowired
    private lateinit var inventoryCommandService: InventoryCommandService

    private var categoryId: Long = 0

    @BeforeEach
    fun setUp() {
        CLEANED_TABLES.forEach { jdbcTemplate.update("DELETE FROM $it") }
        categoryId = categoryCommandService.createCategory(CategoryCreateRequest(name = "소유확인", parentCategoryId = null)).id
    }

    @AfterEach
    fun tearDown() {
        CLEANED_TABLES.forEach { jdbcTemplate.update("DELETE FROM $it") }
    }

    @Test
    fun 판매자가_다른_판매자의_상품을_수정하면_403이고_상품은_그대로다() {
        val productId = saveProduct(SELLER_B, "B의 상품")

        mockMvc.put("/api/v1/products/$productId") {
            contentType = MediaType.APPLICATION_JSON
            content = updateJson("A가 바꾼 이름")
            header("X-User-Id", SELLER_A.toString())
            header("x-user-role", "SELLER")
        }.andExpect { status { isForbidden() } }

        assertThat(productRepository.findNullableById(productId)!!.name).isEqualTo("B의 상품")
    }

    @Test
    fun 판매자가_다른_판매자의_상품을_삭제하면_403이고_상품은_남는다() {
        val productId = saveProduct(SELLER_B, "B의 상품")

        mockMvc.delete("/api/v1/products/$productId") {
            header("X-User-Id", SELLER_A.toString())
            header("x-user-role", "SELLER")
        }.andExpect { status { isForbidden() } }

        assertThat(productRepository.findNullableById(productId)).isNotNull
    }

    @Test
    fun 판매자가_다른_판매자의_상품_재고를_초기화하면_403이고_재고는_생기지_않는다() {
        val productId = saveProduct(SELLER_B, "B의 상품")

        mockMvc.post("/api/v1/inventories") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"product_id":$productId,"stock_quantity":100}"""
            header("X-User-Id", SELLER_A.toString())
            header("x-user-role", "SELLER")
        }.andExpect { status { isForbidden() } }

        assertThat(inventoryRepository.existsByProductId(productId)).isFalse()
    }

    @Test
    fun 판매자가_다른_판매자의_상품_재고를_바꾸면_403이고_재고는_그대로다() {
        val productId = saveProduct(SELLER_B, "B의 상품")
        inventoryCommandService.initStock(InventoryInitRequest(productId = productId, stockQuantity = 10))

        mockMvc.patch("/api/v1/inventories/$productId") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"stock_quantity":0}"""
            header("X-User-Id", SELLER_A.toString())
            header("x-user-role", "SELLER")
        }.andExpect { status { isForbidden() } }

        assertThat(inventoryRepository.findByProductId(productId)!!.stockQuantity).isEqualTo(10)
    }

    @Test
    fun 판매자가_본문에_다른_판매자_id를_넣어_상품을_만들면_로그인한_판매자의_상품이_된다() {
        mockMvc.post("/api/v1/products") {
            contentType = MediaType.APPLICATION_JSON
            content = createJson(SELLER_B)
            header("X-User-Id", SELLER_A.toString())
            header("x-user-role", "SELLER")
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.seller_id") { value(SELLER_A.toInt()) }
        }
    }

    @Test
    fun 판매자는_본문에_판매자_id가_없어도_자기_상품을_만든다() {
        mockMvc.post("/api/v1/products") {
            contentType = MediaType.APPLICATION_JSON
            content = createJson(null)
            header("X-User-Id", SELLER_A.toString())
            header("x-user-role", "SELLER")
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.seller_id") { value(SELLER_A.toInt()) }
        }
    }

    @Test
    fun 관리자는_본문의_판매자_id로_상품을_만든다() {
        mockMvc.post("/api/v1/products") {
            contentType = MediaType.APPLICATION_JSON
            content = createJson(SELLER_B)
            header("X-User-Id", ADMIN.toString())
            header("x-user-role", "ADMIN")
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.seller_id") { value(SELLER_B.toInt()) }
        }
    }

    @Test
    fun 판매자는_자기_상품을_수정하고_재고를_바꾸고_삭제할_수_있다() {
        val productId = saveProduct(SELLER_A, "A의 상품")

        mockMvc.put("/api/v1/products/$productId") {
            contentType = MediaType.APPLICATION_JSON
            content = updateJson("A가 바꾼 이름")
            header("X-User-Id", SELLER_A.toString())
            header("x-user-role", "SELLER")
        }.andExpect { status { isOk() } }
        mockMvc.post("/api/v1/inventories") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"product_id":$productId,"stock_quantity":10}"""
            header("X-User-Id", SELLER_A.toString())
            header("x-user-role", "SELLER")
        }.andExpect { status { isCreated() } }
        mockMvc.patch("/api/v1/inventories/$productId") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"stock_quantity":3}"""
            header("X-User-Id", SELLER_A.toString())
            header("x-user-role", "SELLER")
        }.andExpect { status { isOk() } }
        mockMvc.delete("/api/v1/products/$productId") {
            header("X-User-Id", SELLER_A.toString())
            header("x-user-role", "SELLER")
        }.andExpect { status { isOk() } }

        assertThat(inventoryRepository.findByProductId(productId)!!.stockQuantity).isEqualTo(3)
        assertThat(productRepository.findNullableById(productId)).isNull()
    }

    @Test
    fun 관리자는_다른_판매자의_상품을_수정하고_재고를_바꾸고_삭제할_수_있다() {
        val productId = saveProduct(SELLER_B, "B의 상품")
        inventoryCommandService.initStock(InventoryInitRequest(productId = productId, stockQuantity = 10))

        mockMvc.put("/api/v1/products/$productId") {
            contentType = MediaType.APPLICATION_JSON
            content = updateJson("관리자가 바꾼 이름")
            header("X-User-Id", ADMIN.toString())
            header("x-user-role", "ADMIN")
        }.andExpect { status { isOk() } }
        mockMvc.patch("/api/v1/inventories/$productId") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"stock_quantity":4}"""
            header("X-User-Id", ADMIN.toString())
            header("x-user-role", "ADMIN")
        }.andExpect { status { isOk() } }
        mockMvc.delete("/api/v1/products/$productId") {
            header("X-User-Id", ADMIN.toString())
            header("x-user-role", "ADMIN")
        }.andExpect { status { isOk() } }

        assertThat(inventoryRepository.findByProductId(productId)!!.stockQuantity).isEqualTo(4)
        assertThat(productRepository.findNullableById(productId)).isNull()
    }

    private fun saveProduct(sellerId: Long, name: String): Long =
        productRepository.save(
            Product.create(
                sellerId = sellerId,
                categoryId = categoryId,
                name = name,
                description = "설명",
                price = BigDecimal("1000"),
                status = ProductStatus.AVAILABLE
            )
        ).id!!

    private fun createJson(sellerId: Long?): String {
        val sellerField = sellerId?.let { "\"seller_id\":$it," } ?: ""
        return """{$sellerField"category_id":$categoryId,"name":"새 상품","description":"설명","price":1000}"""
    }

    private fun updateJson(name: String): String =
        """{"category_id":$categoryId,"name":"$name","description":"설명","price":2000}"""

    companion object {
        private const val SELLER_A = 7_101L
        private const val SELLER_B = 7_202L
        private const val ADMIN = 7_303L
        private val CLEANED_TABLES = listOf(
            "inventory_reservations", "inventory_buckets", "inventories",
            "wishlists", "reviews", "product_statistics", "product_images", "products", "categories"
        )
    }
}
