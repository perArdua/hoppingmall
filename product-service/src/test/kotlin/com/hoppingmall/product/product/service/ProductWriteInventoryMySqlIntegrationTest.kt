package com.hoppingmall.product.product.service

import com.hoppingmall.idempotency.Idempotent
import com.hoppingmall.common.UserPrincipal
import com.hoppingmall.product.category.dto.request.CategoryCreateRequest
import com.hoppingmall.product.category.service.CategoryCommandService
import com.hoppingmall.product.common.enums.ProductStatus
import com.hoppingmall.product.inventory.dto.request.InventoryUpdateRequest
import com.hoppingmall.product.inventory.exception.InventoryNotFoundException
import com.hoppingmall.product.inventory.service.InventoryCommandService
import com.hoppingmall.product.inventory.service.InventoryQueryService
import com.hoppingmall.product.product.controller.ProductController
import com.hoppingmall.product.product.dto.request.ProductCreateRequest
import com.hoppingmall.product.product.dto.request.ProductUpdateRequest
import com.hoppingmall.product.support.MySqlIntegrationTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import java.math.BigDecimal

abstract class ProductWriteInventoryScenarios : MySqlIntegrationTestSupport() {

    @Autowired
    protected lateinit var productCommandService: ProductCommandService

    @Autowired
    protected lateinit var inventoryCommandService: InventoryCommandService

    @Autowired
    protected lateinit var inventoryQueryService: InventoryQueryService

    @Autowired
    private lateinit var categoryCommandService: CategoryCommandService

    protected var categoryId: Long = 0

    protected abstract fun categoryName(): String

    protected abstract fun committedStock(productId: Long): Int?

    @BeforeEach
    fun seedCategory() {
        cleanUp()
        categoryId = categoryCommandService.createCategory(CategoryCreateRequest(name = categoryName(), parentCategoryId = null)).id
    }

    @AfterEach
    fun cleanUp() {
        jdbcTemplate.update("DELETE FROM product_images WHERE product_id IN (SELECT id FROM products WHERE seller_id = ?)", SELLER_ID)
        jdbcTemplate.update("DELETE FROM products WHERE seller_id = ?", SELLER_ID)
        jdbcTemplate.update("DELETE FROM categories WHERE name = ?", categoryName())
    }

    @Test
    fun 등록하면_보낸_수량으로_재고를_함께_만든다() {
        val product = productCommandService.createProduct(createRequest(stockQuantity = 7))

        assertThat(committedStock(product.id)).isEqualTo(7)
        assertThat(inventoryQueryService.getStock(product.id).stockQuantity).isEqualTo(7)
    }

    @Test
    fun 등록에서_재고_수량을_보내지_않으면_재고_0으로_만든다() {
        val product = productCommandService.createProduct(createRequest(stockQuantity = null))

        assertThat(committedStock(product.id)).isEqualTo(0)
    }

    @Test
    fun 삭제하면_재고_조회와_고치기가_재고_없음이다() {
        val product = productCommandService.createProduct(createRequest(stockQuantity = 5))

        productCommandService.deleteProduct(product.id)

        assertThatThrownBy { inventoryQueryService.getStock(product.id) }
            .isInstanceOf(InventoryNotFoundException::class.java)
        assertThatThrownBy {
            inventoryCommandService.updateStock(product.id, InventoryUpdateRequest(stockQuantity = 9))
        }.isInstanceOf(InventoryNotFoundException::class.java)
        assertThat(deletedAtOfInventory(product.id)).isNotNull()
    }

    @Test
    fun 삭제된_상품의_재고도_취소와_환불의_복원은_받는다() {
        val product = productCommandService.createProduct(createRequest(stockQuantity = 5))
        val reservationId = inventoryCommandService.reserveStock(product.id, 2)
        productCommandService.deleteProduct(product.id)

        inventoryCommandService.cancelReservation(reservationId)
        inventoryCommandService.increaseStock(product.id, 1)

        assertThat(committedStock(product.id)).isEqualTo(6)
    }

    @Test
    fun 수정에서_상태를_보내지_않으면_판매자가_둔_품절_상태를_그대로_둔다() {
        val product = productCommandService.createProduct(createRequest(status = ProductStatus.SOLD_OUT))

        val updated = productCommandService.updateProduct(product.id, updateRequest(status = null, imageUrls = null))

        assertThat(updated.status).isEqualTo(ProductStatus.SOLD_OUT)
        assertThat(statusOf(product.id)).isEqualTo("SOLD_OUT")
    }

    @Test
    fun 수정에서_상태를_보내면_바꾼다() {
        val product = productCommandService.createProduct(createRequest(status = ProductStatus.SOLD_OUT))

        val updated = productCommandService.updateProduct(product.id, updateRequest(status = ProductStatus.AVAILABLE, imageUrls = null))

        assertThat(updated.status).isEqualTo(ProductStatus.AVAILABLE)
    }

    @Test
    fun 수정에서_이미지를_보내지_않으면_기존_이미지를_그대로_둔다() {
        val product = productCommandService.createProduct(createRequest(imageUrls = listOf("/a.jpg", "/b.jpg")))

        val updated = productCommandService.updateProduct(product.id, updateRequest(status = null, imageUrls = null))

        assertThat(updated.imageUrls).containsExactly("/a.jpg", "/b.jpg")
        assertThat(activeImages(product.id)).containsExactly("/a.jpg", "/b.jpg")
    }

    @Test
    fun 수정에서_이미지를_보내면_그_목록으로_바꾼다() {
        val product = productCommandService.createProduct(createRequest(imageUrls = listOf("/a.jpg")))

        val updated = productCommandService.updateProduct(product.id, updateRequest(status = null, imageUrls = listOf("/c.jpg")))

        assertThat(updated.imageUrls).containsExactly("/c.jpg")
        assertThat(activeImages(product.id)).containsExactly("/c.jpg")
    }

    @Test
    fun 상품_등록_입구는_멱등성_키를_받는다() {
        val method = ProductController::class.java.getMethod("createProduct", ProductCreateRequest::class.java, UserPrincipal::class.java)

        assertThat(method.getAnnotation(Idempotent::class.java)).isNotNull
    }

    private fun createRequest(
        stockQuantity: Int? = null,
        status: ProductStatus = ProductStatus.AVAILABLE,
        imageUrls: List<String>? = null
    ) = ProductCreateRequest(
        sellerId = SELLER_ID,
        categoryId = categoryId,
        name = "재고 함께 등록",
        description = "설명",
        price = BigDecimal("1000"),
        imageUrls = imageUrls,
        status = status,
        stockQuantity = stockQuantity
    )

    private fun updateRequest(status: ProductStatus?, imageUrls: List<String>?): ProductUpdateRequest {
        val base = ProductUpdateRequest(categoryId = categoryId, name = "바뀐 이름", description = "바뀐 설명", price = BigDecimal("2000"), imageUrls = imageUrls)
        return if (status == null) base else base.copy(status = status)
    }

    private fun statusOf(productId: Long): String =
        jdbcTemplate.queryForObject("SELECT status FROM products WHERE id = ?", String::class.java, productId)!!

    private fun activeImages(productId: Long): List<String> =
        jdbcTemplate.queryForList(
            "SELECT image_url FROM product_images WHERE product_id = ? AND deleted_at IS NULL ORDER BY sort_order",
            String::class.java,
            productId
        )

    private fun deletedAtOfInventory(productId: Long): Any? =
        jdbcTemplate.queryForList("SELECT deleted_at FROM inventories WHERE product_id = ?", productId).single()["deleted_at"]

    protected companion object {
        const val SELLER_ID = 97_400L
    }
}

@DisplayName("상품 등록·수정·삭제와 재고 MySQL 통합 테스트 (single 모드)")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class SingleProductWriteInventoryMySqlIntegrationTest : ProductWriteInventoryScenarios() {

    override fun categoryName(): String = "재고함께-single"

    override fun committedStock(productId: Long): Int? =
        jdbcTemplate.queryForList("SELECT stock_quantity FROM inventories WHERE product_id = ?", Int::class.java, productId).singleOrNull()
}

@DisplayName("상품 등록·수정·삭제와 재고 MySQL 통합 테스트 (bucket 모드)")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@TestPropertySource(properties = ["inventory.mode=bucket", "inventory.bucket.count=8", "inventory.semaphore.permits=64"])
class BucketProductWriteInventoryMySqlIntegrationTest : ProductWriteInventoryScenarios() {

    override fun categoryName(): String = "재고함께-bucket"

    override fun committedStock(productId: Long): Int? =
        jdbcTemplate.queryForList("SELECT SUM(stock_quantity) FROM inventory_buckets WHERE product_id = ?", Int::class.java, productId).singleOrNull()
}
