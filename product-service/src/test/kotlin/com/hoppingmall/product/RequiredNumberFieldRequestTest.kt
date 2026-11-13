package com.hoppingmall.product

import com.hoppingmall.product.category.dto.request.CategoryCreateRequest
import com.hoppingmall.product.category.service.CategoryCommandService
import com.hoppingmall.product.common.enums.ProductStatus
import com.hoppingmall.product.inventory.domain.repository.InventoryRepository
import com.hoppingmall.product.inventory.dto.request.InventoryInitRequest
import com.hoppingmall.product.inventory.service.InventoryCommandService
import com.hoppingmall.product.product.domain.Product
import com.hoppingmall.product.product.domain.repository.ProductRepository
import com.hoppingmall.product.support.TestCacheConfig
import com.hoppingmall.product.wishlist.domain.repository.WishlistRepository
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
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import java.math.BigDecimal

@DisplayName("요청 본문의 필수 숫자 필드가 빠졌을 때의 응답")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@SpringBootTest(properties = ["spring.main.allow-bean-definition-overriding=true"])
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestCacheConfig::class)
class RequiredNumberFieldRequestTest {

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

    @Autowired
    private lateinit var wishlistRepository: WishlistRepository

    private var categoryId: Long = 0

    @BeforeEach
    fun setUp() {
        CLEANED_TABLES.forEach { jdbcTemplate.update("DELETE FROM $it") }
        categoryId = categoryCommandService.createCategory(CategoryCreateRequest(name = "필수필드", parentCategoryId = null)).id
    }

    @AfterEach
    fun tearDown() {
        CLEANED_TABLES.forEach { jdbcTemplate.update("DELETE FROM $it") }
    }

    @Test
    fun 관리자가_판매자_id_없이_상품을_만들면_400이고_상품은_생기지_않는다() {
        mockMvc.post("/api/v1/products") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"category_id":$categoryId,"name":"관리자 상품","description":"설명","price":1000}"""
            header("X-User-Id", ADMIN.toString())
            header("x-user-role", "ADMIN")
        }.andExpect { status { isBadRequest() } }

        assertThat(productRepository.count()).isZero()
    }

    @Test
    fun 상품_생성_본문에_카테고리_id가_없으면_400이고_상품은_생기지_않는다() {
        mockMvc.post("/api/v1/products") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"새 상품","description":"설명","price":1000}"""
            header("X-User-Id", SELLER.toString())
            header("x-user-role", "SELLER")
        }.andExpect { status { isBadRequest() } }

        assertThat(productRepository.count()).isZero()
    }

    @Test
    fun 상품_수정_본문에_카테고리_id가_없으면_400이고_상품은_그대로다() {
        val productId = saveProduct()

        mockMvc.put("/api/v1/products/$productId") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"바뀐 이름","description":"설명","price":2000}"""
            header("X-User-Id", SELLER.toString())
            header("x-user-role", "SELLER")
        }.andExpect { status { isBadRequest() } }

        assertThat(productRepository.findNullableById(productId)!!.name).isEqualTo(PRODUCT_NAME)
    }

    @Test
    fun 재고_초기화_본문에_재고_수량이_없으면_400이고_재고는_생기지_않는다() {
        val productId = saveProduct()

        mockMvc.post("/api/v1/inventories") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"product_id":$productId}"""
            header("X-User-Id", SELLER.toString())
            header("x-user-role", "SELLER")
        }.andExpect { status { isBadRequest() } }

        assertThat(inventoryRepository.existsByProductId(productId)).isFalse()
    }

    @Test
    fun 재고_초기화_본문에_상품_id가_없으면_400이다() {
        mockMvc.post("/api/v1/inventories") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"stock_quantity":100}"""
            header("X-User-Id", SELLER.toString())
            header("x-user-role", "SELLER")
        }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun 재고_변경_본문에_재고_수량이_없으면_400이고_재고는_그대로다() {
        val productId = saveProductWithStock(10)

        mockMvc.patch("/api/v1/inventories/$productId") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"stockQuantity":50}"""
            header("X-User-Id", SELLER.toString())
            header("x-user-role", "SELLER")
        }.andExpect { status { isBadRequest() } }

        assertThat(inventoryRepository.findByProductId(productId)!!.stockQuantity).isEqualTo(10)
    }

    @Test
    fun 재고_변경_본문의_재고_수량이_null이면_400이고_재고는_그대로다() {
        val productId = saveProductWithStock(10)

        mockMvc.patch("/api/v1/inventories/$productId") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"stock_quantity":null}"""
            header("X-User-Id", SELLER.toString())
            header("x-user-role", "SELLER")
        }.andExpect { status { isBadRequest() } }

        assertThat(inventoryRepository.findByProductId(productId)!!.stockQuantity).isEqualTo(10)
    }

    @Test
    fun 찜_추가_본문에_상품_id가_없으면_400이고_찜은_생기지_않는다() {
        mockMvc.post("/api/v1/wishlists") {
            contentType = MediaType.APPLICATION_JSON
            content = """{}"""
            header("X-User-Id", BUYER.toString())
            header("x-user-role", "BUYER")
        }.andExpect { status { isBadRequest() } }

        assertThat(wishlistRepository.count()).isZero()
    }

    private fun saveProduct(): Long =
        productRepository.save(
            Product.create(
                sellerId = SELLER,
                categoryId = categoryId,
                name = PRODUCT_NAME,
                description = "설명",
                price = BigDecimal("1000"),
                status = ProductStatus.AVAILABLE
            )
        ).id!!

    private fun saveProductWithStock(stockQuantity: Int): Long {
        val productId = saveProduct()
        inventoryCommandService.initStock(InventoryInitRequest(productId = productId, stockQuantity = stockQuantity))
        return productId
    }

    companion object {
        private const val SELLER = 7_401L
        private const val BUYER = 7_402L
        private const val ADMIN = 7_403L
        private const val PRODUCT_NAME = "필수 필드 상품"
        private val CLEANED_TABLES = listOf(
            "inventory_reservations", "inventory_buckets", "inventories",
            "wishlists", "reviews", "product_statistics", "product_images", "products", "categories"
        )
    }
}
