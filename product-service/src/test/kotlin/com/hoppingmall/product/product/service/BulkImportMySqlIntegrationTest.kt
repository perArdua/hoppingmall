package com.hoppingmall.product.product.service

import com.hoppingmall.product.category.dto.request.CategoryCreateRequest
import com.hoppingmall.product.category.service.CategoryCommandService
import com.hoppingmall.product.support.MySqlIntegrationTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.mock.web.MockMultipartFile

@DisplayName("상품 대량 등록 MySQL 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class BulkImportMySqlIntegrationTest : MySqlIntegrationTestSupport() {

    @Autowired
    private lateinit var bulkImportService: BulkImportService

    @Autowired
    private lateinit var categoryCommandService: CategoryCommandService

    @BeforeEach
    fun cleanCatalog() {
        CATALOG_TABLES.forEach { jdbcTemplate.update("DELETE FROM $it") }
    }

    @AfterEach
    fun cleanCatalogAfter() {
        CATALOG_TABLES.forEach { jdbcTemplate.update("DELETE FROM $it") }
    }

    @Test
    fun 대량_등록은_커밋_뒤_상품과_재고를_만들고_COMPLETED로_끝난다() {
        val categoryId = createCategory()
        val csv = """
            name,description,categoryId,price,stockQuantity
            대량상품1,설명1,$categoryId,10000,5
            대량상품2,설명2,$categoryId,20000,7
        """.trimIndent()

        val started = bulkImportService.startImport(SELLER_ID, csvFile(csv))

        val job = awaitFinished(started.jobId)
        assertThat(job["status"]).isEqualTo("COMPLETED")
        assertThat(job["success_count"]).isEqualTo(2)
        assertThat(job["fail_count"]).isEqualTo(0)
        assertThat(job["processed_rows"]).isEqualTo(2)
        assertThat(job["completed_at"]).isNotNull()
        assertThat(sellerProductNames()).containsExactlyInAnyOrder("대량상품1", "대량상품2")
        assertThat(sellerStocks()).containsExactlyInAnyOrder(5, 7)
        assertThat(sellerImageCount()).isEqualTo(2)
    }

    @Test
    fun 처리_중_DB_오류로_트랜잭션이_롤백되면_작업은_FAILED로_끝나고_상품은_남지_않는다() {
        val categoryId = createCategory()
        val tooLongName = "가".repeat(300)
        val csv = """
            name,description,categoryId,price,stockQuantity
            정상상품,설명1,$categoryId,10000,5
            $tooLongName,설명2,$categoryId,20000,7
        """.trimIndent()

        val started = bulkImportService.startImport(SELLER_ID, csvFile(csv))

        val job = awaitFinished(started.jobId)
        assertThat(job["status"]).isEqualTo("FAILED")
        assertThat(job["completed_at"]).isNotNull()
        assertThat(sellerProductNames()).isEmpty()
        assertThat(sellerStocks()).isEmpty()
    }

    private fun awaitFinished(jobId: Long): Map<String, Any?> {
        val deadline = System.currentTimeMillis() + WAIT_LIMIT_MS
        var job = findJob(jobId)
        while (job["status"] == "IMPORTING" && System.currentTimeMillis() < deadline) {
            Thread.onSpinWait()
            job = findJob(jobId)
        }
        return job
    }

    private fun findJob(jobId: Long): Map<String, Any?> =
        jdbcTemplate.queryForMap(
            "SELECT status, processed_rows, success_count, fail_count, completed_at FROM bulk_import_jobs WHERE id = ?",
            jobId
        )

    private fun sellerProductNames(): List<String> =
        jdbcTemplate.queryForList("SELECT name FROM products WHERE seller_id = ?", String::class.java, SELLER_ID)

    private fun sellerStocks(): List<Int> =
        jdbcTemplate.queryForList(
            "SELECT i.stock_quantity FROM inventories i JOIN products p ON p.id = i.product_id WHERE p.seller_id = ?",
            Int::class.java, SELLER_ID
        )

    private fun sellerImageCount(): Int =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM product_images pi JOIN products p ON p.id = pi.product_id WHERE p.seller_id = ?",
            Int::class.java, SELLER_ID
        )!!

    private fun createCategory(): Long =
        categoryCommandService.createCategory(CategoryCreateRequest(name = "대량등록-카테고리")).id

    private fun csvFile(content: String) =
        MockMultipartFile("file", "products.csv", "text/csv", content.toByteArray(Charsets.UTF_8))

    companion object {
        private const val SELLER_ID = 9_188L
        private const val WAIT_LIMIT_MS = 15_000L
        private val CATALOG_TABLES = listOf(
            "bulk_import_jobs", "inventories", "wishlists", "reviews", "product_statistics", "product_images", "products", "categories"
        )
    }
}
