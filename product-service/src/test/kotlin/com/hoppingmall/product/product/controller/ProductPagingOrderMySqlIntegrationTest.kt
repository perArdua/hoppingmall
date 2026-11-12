package com.hoppingmall.product.product.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.hoppingmall.product.support.MySqlIntegrationTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.time.LocalDateTime

@DisplayName("상품 목록·검색 페이지 정렬 MySQL 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@AutoConfigureMockMvc
class ProductPagingOrderMySqlIntegrationTest : MySqlIntegrationTestSupport() {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @BeforeEach
    fun cleanProducts() {
        jdbcTemplate.update("DELETE FROM products")
    }

    @AfterEach
    fun stopStatementLog() {
        jdbcTemplate.execute("SET GLOBAL general_log = 'OFF'")
        jdbcTemplate.update("DELETE FROM products")
    }

    @Test
    fun 정렬_없이_상품_목록을_넘기면_모든_상품이_한_번씩_나온다() {
        val ids = insertProducts(PRODUCT_COUNT)

        assertThat(walk("/api/v1/products")).containsExactlyInAnyOrderElementsOf(ids)
    }

    @Test
    fun 정렬_없이_판매자별_목록을_넘기면_모든_상품이_한_번씩_나온다() {
        val ids = insertProducts(PRODUCT_COUNT)

        assertThat(walk("/api/v1/products/seller/$SELLER_ID")).containsExactlyInAnyOrderElementsOf(ids)
    }

    @Test
    fun 정렬_없이_카테고리별_목록을_넘기면_모든_상품이_한_번씩_나온다() {
        val ids = insertProducts(PRODUCT_COUNT)

        assertThat(walk("/api/v1/products/category/$CATEGORY_ID")).containsExactlyInAnyOrderElementsOf(ids)
    }

    @Test
    fun 키워드_없이_검색을_넘기면_모든_상품이_한_번씩_나온다() {
        val ids = insertProducts(PRODUCT_COUNT)

        assertThat(walk("/api/v1/products/search?categoryId=$CATEGORY_ID")).containsExactlyInAnyOrderElementsOf(ids)
    }

    @Test
    fun 정렬_없이_상품_목록을_요청하면_쿼리가_상품_id로_정렬한다() {
        insertProducts(PRODUCT_COUNT)

        val statements = productSelectsDuring { page("/api/v1/products", 1) }

        assertThat(statements).isNotEmpty()
        statements.forEach { assertThat(it).containsPattern(ORDER_BY_ID) }
    }

    @Test
    fun 정렬_없이_판매자별_목록을_요청하면_쿼리가_상품_id로_정렬한다() {
        insertProducts(PRODUCT_COUNT)

        val statements = productSelectsDuring { page("/api/v1/products/seller/$SELLER_ID", 1) }

        assertThat(statements).isNotEmpty()
        statements.forEach { assertThat(it).containsPattern(ORDER_BY_ID) }
    }

    @Test
    fun 정렬_없이_카테고리별_목록을_요청하면_쿼리가_상품_id로_정렬한다() {
        insertProducts(PRODUCT_COUNT)

        val statements = productSelectsDuring { page("/api/v1/products/category/$CATEGORY_ID", 1) }

        assertThat(statements).isNotEmpty()
        statements.forEach { assertThat(it).containsPattern(ORDER_BY_ID) }
    }

    @Test
    fun 키워드_없이_검색하면_쿼리가_상품_id로_정렬한다() {
        insertProducts(PRODUCT_COUNT)

        val statements = productSelectsDuring { page("/api/v1/products/search?categoryId=$CATEGORY_ID", 1) }

        assertThat(statements).isNotEmpty()
        statements.forEach { assertThat(it).containsPattern(ORDER_BY_ID) }
    }

    @Test
    fun 키워드로_검색하면_관련도가_같은_상품은_상품_id로_정렬하고_모든_상품이_한_번씩_나온다() {
        val ids = insertProducts(PRODUCT_COUNT)
        jdbcTemplate.execute("ALTER TABLE products ADD FULLTEXT INDEX $FULLTEXT_INDEX (name, description)")
        try {
            val statements = productSelectsDuring { page("/api/v1/products/search?keyword=sneakers", 1) }

            assertThat(statements).isNotEmpty()
            statements.forEach { assertThat(it).containsPattern(FULLTEXT_ORDER_BY_ID) }
            assertThat(walk("/api/v1/products/search?keyword=sneakers")).containsExactlyInAnyOrderElementsOf(ids)
        } finally {
            jdbcTemplate.execute("ALTER TABLE products DROP INDEX $FULLTEXT_INDEX")
        }
    }

    private fun walk(path: String): List<Long> =
        (0 until PAGE_COUNT).flatMap { page(path, it) }

    private fun page(path: String, page: Int): List<Long> {
        val separator = if (path.contains("?")) "&" else "?"
        val body = mockMvc.get("$path${separator}page=$page&size=$PAGE_SIZE") {
            header("X-User-Id", "1")
            header("x-user-role", "BUYER")
        }.andExpect {
            status { isOk() }
        }.andReturn().response.getContentAsString(Charsets.UTF_8)
        return objectMapper.readTree(body).path("data").path("content").map { it.path("id").asLong() }
    }

    private fun productSelectsDuring(action: () -> Unit): List<String> {
        jdbcTemplate.execute("SET GLOBAL log_output = 'TABLE'")
        jdbcTemplate.execute("TRUNCATE TABLE mysql.general_log")
        jdbcTemplate.execute("SET GLOBAL general_log = 'ON'")
        action()
        jdbcTemplate.execute("SET GLOBAL general_log = 'OFF'")
        return jdbcTemplate.queryForList(
            "SELECT CONVERT(argument USING utf8mb4) FROM mysql.general_log WHERE command_type = 'Query'",
            String::class.java
        )
            .map { it.lowercase().replace(Regex("/\\*.*?\\*/"), " ").replace(Regex("\\s+"), " ").trim() }
            .filter { it.startsWith("select") && Regex("from products\\b").containsMatchIn(it) && it.contains(" limit ") }
    }

    private fun insertProducts(count: Int): List<Long> {
        val now = LocalDateTime.now().withNano(0)
        repeat(count) {
            jdbcTemplate.update(
                "INSERT INTO products (created_at, updated_at, seller_id, category_id, name, description, price, status) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, 'AVAILABLE')",
                now, now, SELLER_ID, CATEGORY_ID, "sneakers", "sneakers", 10000
            )
        }
        return jdbcTemplate.queryForList("SELECT id FROM products", Long::class.javaObjectType)
    }

    companion object {
        private const val SELLER_ID = 9_101L
        private const val CATEGORY_ID = 9_201L
        private const val PRODUCT_COUNT = 25
        private const val PAGE_SIZE = 10
        private const val PAGE_COUNT = 3
        private const val FULLTEXT_INDEX = "ft_products_paging_test"
        private val ORDER_BY_ID = Regex("order by (\\w+\\.)?id( asc)? limit ").toPattern()
        private val FULLTEXT_ORDER_BY_ID = Regex("desc, (\\w+\\.)?id( asc)? limit ").toPattern()
    }
}
