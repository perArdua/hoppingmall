package com.hoppingmall.product.product.service

import com.hoppingmall.product.product.dto.request.ProductSearchCondition
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
import java.time.LocalDateTime

@DisplayName("상품 키워드 검색 MySQL 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class ProductKeywordSearchMySqlIntegrationTest : MySqlIntegrationTestSupport() {

    @Autowired
    private lateinit var productQueryService: ProductQueryService

    @BeforeEach
    fun cleanProducts() {
        jdbcTemplate.update("DELETE FROM products")
    }

    @AfterEach
    fun cleanProductsAfter() {
        jdbcTemplate.update("DELETE FROM products")
    }

    @Test
    fun 기동한_뒤_상품_이름과_설명에_FULLTEXT_인덱스가_있다() {
        val fullTextColumns = jdbcTemplate.queryForList(
            "SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY COLUMN_NAME) FROM information_schema.STATISTICS " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'products' AND INDEX_TYPE = 'FULLTEXT' GROUP BY INDEX_NAME",
            String::class.java
        )

        assertThat(fullTextColumns).contains("description,name")
    }

    @Test
    fun 키워드로_검색하면_오류_없이_이름이나_설명이_맞는_상품만_나온다() {
        val byName = insertProduct("가벼운 운동화", "매일 신는 신발")
        val byDescription = insertProduct("러닝화", "쿠션 좋은 운동화")
        insertProduct("겨울 패딩", "따뜻한 외투")

        val found = productQueryService.searchProducts(ProductSearchCondition(keyword = "운동화"), PageRequest.of(0, 20))

        assertThat(found.content.map { it.id }).containsExactlyInAnyOrder(byName, byDescription)
    }

    @Test
    fun 키워드와_조건을_함께_주어도_오류_없이_검색된다() {
        val matched = insertProduct("가벼운 운동화", "매일 신는 신발", categoryId = CATEGORY_ID)
        insertProduct("무거운 운동화", "등산용 신발", categoryId = CATEGORY_ID + 1)

        val found = productQueryService.searchProducts(
            ProductSearchCondition(keyword = "운동화", categoryId = CATEGORY_ID),
            PageRequest.of(0, 20)
        )

        assertThat(found.content.map { it.id }).containsExactly(matched)
    }

    private fun insertProduct(name: String, description: String, categoryId: Long = CATEGORY_ID): Long {
        val now = LocalDateTime.now().withNano(0)
        jdbcTemplate.update(
            "INSERT INTO products (created_at, updated_at, seller_id, category_id, name, description, price, status) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, 'AVAILABLE')",
            now, now, SELLER_ID, categoryId, name, description, 10000
        )
        return jdbcTemplate.queryForObject("SELECT MAX(id) FROM products", Long::class.java)!!
    }

    companion object {
        private const val SELLER_ID = 7_511L
        private const val CATEGORY_ID = 7_512L
    }
}
