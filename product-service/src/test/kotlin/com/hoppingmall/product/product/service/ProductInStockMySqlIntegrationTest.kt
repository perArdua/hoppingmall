package com.hoppingmall.product.product.service

import com.hoppingmall.product.category.dto.request.CategoryCreateRequest
import com.hoppingmall.product.category.service.CategoryCommandService
import com.hoppingmall.product.common.enums.ProductStatus
import com.hoppingmall.product.inventory.domain.Inventory
import com.hoppingmall.product.inventory.domain.repository.InventoryRepository
import com.hoppingmall.product.inventory.service.InventoryCommandService
import com.hoppingmall.product.product.domain.Product
import com.hoppingmall.product.product.domain.repository.ProductRepository
import com.hoppingmall.product.product.dto.response.ProductResponse
import com.hoppingmall.product.support.MySqlIntegrationTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.test.context.TestPropertySource
import java.math.BigDecimal

abstract class ProductInStockScenarios : MySqlIntegrationTestSupport() {

    @Autowired
    protected lateinit var productQueryService: ProductQueryService

    @Autowired
    protected lateinit var productStockReader: ProductStockReader

    @Autowired
    protected lateinit var productRepository: ProductRepository

    @Autowired
    protected lateinit var inventoryRepository: InventoryRepository

    @Autowired
    protected lateinit var inventoryCommandService: InventoryCommandService

    @Autowired
    private lateinit var categoryCommandService: CategoryCommandService

    private var categoryId: Long = 0

    protected abstract fun categoryName(): String

    protected abstract fun seedStock(productId: Long, total: Int)

    @BeforeEach
    fun seedCategory() {
        cleanUp()
        categoryId = categoryCommandService.createCategory(CategoryCreateRequest(name = categoryName(), parentCategoryId = null)).id
    }

    @AfterEach
    fun cleanUp() {
        jdbcTemplate.update("DELETE FROM products WHERE seller_id = ?", SELLER_ID)
        jdbcTemplate.update("DELETE FROM categories WHERE name = ?", categoryName())
    }

    @Test
    fun 목록의_재고_여부는_재고_수량으로_정하고_수량은_내보내지_않는다() {
        val inStock = saveProduct("재고 있음", ProductStatus.AVAILABLE, stock = 8)
        val outOfStock = saveProduct("재고 0", ProductStatus.AVAILABLE, stock = 0)
        val noInventory = saveProduct("재고 행 없음", ProductStatus.AVAILABLE, stock = null)

        val lists = listOf(
            productQueryService.getProducts(PAGE).content,
            productQueryService.getProductsBySellerId(SELLER_ID, PAGE).content,
            productQueryService.getProductsByCategoryId(categoryId, PAGE).content
        )

        lists.forEach { content ->
            val byId = content.associateBy { it.id }
            assertThat(byId[inStock]!!.inStock).isTrue()
            assertThat(byId[outOfStock]!!.inStock).isFalse()
            assertThat(byId[noInventory]!!.inStock).isFalse()
            assertThat(content.map { it.stockQuantity }).containsOnlyNulls()
        }
    }

    @Test
    fun 판매자_재고_목록은_재고_수량을_함께_준다() {
        val inStock = saveProduct("재고 있음", ProductStatus.AVAILABLE, stock = 8)
        val noInventory = saveProduct("재고 행 없음", ProductStatus.AVAILABLE, stock = null)

        val byId = productQueryService.getProductsBySellerIdWithStock(SELLER_ID, PAGE).content.associateBy { it.id }

        assertThat(byId[inStock]!!.stockQuantity).isEqualTo(8)
        assertThat(byId[inStock]!!.inStock).isTrue()
        assertThat(byId[noInventory]!!.stockQuantity).isEqualTo(0)
        assertThat(byId[noInventory]!!.inStock).isFalse()
    }

    @Test
    fun 판매자가_내린_품절_상태와_재고_없음은_따로_나온다() {
        val soldOutWithStock = saveProduct("판매자가 품절로 둠", ProductStatus.SOLD_OUT, stock = 8)
        val availableWithoutStock = saveProduct("재고만 없음", ProductStatus.AVAILABLE, stock = 0)

        val byId = productQueryService.getProducts(PAGE).content.associateBy { it.id }

        assertThat(byId[soldOutWithStock]!!.status).isEqualTo(ProductStatus.SOLD_OUT)
        assertThat(byId[soldOutWithStock]!!.inStock).isTrue()
        assertThat(byId[availableWithoutStock]!!.status).isEqualTo(ProductStatus.AVAILABLE)
        assertThat(byId[availableWithoutStock]!!.inStock).isFalse()
    }

    @Test
    fun 상세의_재고_여부는_상품_캐시와_따로_지금_재고를_따른다() {
        val productId = saveProduct("마지막 재고", ProductStatus.AVAILABLE, stock = 8)
        assertThat(detail(productId).inStock).isTrue()

        inventoryCommandService.reserveStock(productId, 8)

        val after = detail(productId)
        assertThat(after.inStock).isFalse()
        assertThat(after.stockQuantity).isNull()
        assertThat(productRepository.findNullableById(productId)!!.status).isEqualTo(ProductStatus.AVAILABLE)
    }

    @Test
    fun 평점은_소수_1자리로_반올림해_준다() {
        val up = saveProduct("반올림 올림", ProductStatus.AVAILABLE, stock = 1)
        val down = saveProduct("반올림 내림", ProductStatus.AVAILABLE, stock = 1)
        jdbcTemplate.update("UPDATE products SET average_rating = 4.25, review_count = 4 WHERE id = ?", up)
        jdbcTemplate.update("UPDATE products SET average_rating = 4.24, review_count = 5 WHERE id = ?", down)

        val byId = productQueryService.getProducts(PAGE).content.associateBy { it.id }

        assertThat(byId[up]!!.averageRating).isEqualByComparingTo("4.3")
        assertThat(byId[up]!!.averageRating.scale()).isEqualTo(1)
        assertThat(byId[down]!!.averageRating).isEqualByComparingTo("4.2")
        assertThat(detail(up).averageRating).isEqualByComparingTo("4.3")
    }

    private fun detail(productId: Long): ProductResponse =
        productStockReader.withInStock(productQueryService.getProductById(productId)!!)

    private fun saveProduct(name: String, status: ProductStatus, stock: Int?): Long {
        val productId = productRepository.saveAndFlush(
            Product.create(SELLER_ID, categoryId, name, "설명", BigDecimal("1000"), status)
        ).id!!
        if (stock != null) seedStock(productId, stock)
        return productId
    }

    protected companion object {
        const val SELLER_ID = 97_500L
        val PAGE: PageRequest = PageRequest.of(0, 50, Sort.by("id"))
    }
}

@DisplayName("상품 목록·상세의 재고 여부 MySQL 통합 테스트 (single 모드)")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class SingleProductInStockMySqlIntegrationTest : ProductInStockScenarios() {

    override fun categoryName(): String = "재고여부-single"

    override fun seedStock(productId: Long, total: Int) {
        inventoryRepository.saveAndFlush(Inventory.create(productId = productId, stockQuantity = total))
    }
}

@DisplayName("상품 목록·상세의 재고 여부 MySQL 통합 테스트 (bucket 모드)")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@TestPropertySource(properties = ["inventory.mode=bucket", "inventory.bucket.count=8", "inventory.semaphore.permits=64"])
class BucketProductInStockMySqlIntegrationTest : ProductInStockScenarios() {

    override fun categoryName(): String = "재고여부-bucket"

    override fun seedStock(productId: Long, total: Int) {
        inventoryRepository.saveAndFlush(Inventory.create(productId = productId, stockQuantity = 999))
        (0 until 8).forEach { bucketNo ->
            jdbcTemplate.update(
                "INSERT INTO inventory_buckets (product_id, bucket_no, stock_quantity) VALUES (?, ?, ?)",
                productId, bucketNo, total / 8
            )
        }
    }
}
