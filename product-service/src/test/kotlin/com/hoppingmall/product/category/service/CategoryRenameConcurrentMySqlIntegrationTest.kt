package com.hoppingmall.product.category.service

import com.hoppingmall.common.BusinessException
import com.hoppingmall.product.category.dto.request.CategoryCreateRequest
import com.hoppingmall.product.category.dto.request.CategoryUpdateRequest
import com.hoppingmall.product.support.MySqlIntegrationTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus

@DisplayName("카테고리 이름 변경 동시 중복 MySQL 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class CategoryRenameConcurrentMySqlIntegrationTest : MySqlIntegrationTestSupport() {

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
    fun 같은_이름의_카테고리가_만들어지는_중에_이름을_바꾸면_늦은_쪽은_409_오류를_받는다() {
        val categoryId = categoryCommandService.createCategory(CategoryCreateRequest(name = ORIGINAL_NAME)).id

        val result = runWhileHolding(
            holdingChanges = { connection ->
                connection.prepareStatement(
                    "INSERT INTO categories (name, parent_category_id, depth, created_at) VALUES (?, NULL, 0, NOW())"
                ).use {
                    it.setString(1, TARGET_NAME)
                    it.executeUpdate()
                }
            },
            actionName = "카테고리 이름 변경",
            waitTable = "categories"
        ) {
            categoryCommandService.updateCategory(categoryId, CategoryUpdateRequest(name = TARGET_NAME))
        }

        assertConflict(result)
        assertThat(nameOf(categoryId)).isEqualTo(ORIGINAL_NAME)
        assertThat(countByName(TARGET_NAME)).isEqualTo(1L)
    }

    @Test
    fun 두_카테고리의_이름을_같은_이름으로_동시에_바꾸면_늦은_쪽은_409_오류를_받는다() {
        val firstId = categoryCommandService.createCategory(CategoryCreateRequest(name = ORIGINAL_NAME)).id
        val secondId = categoryCommandService.createCategory(CategoryCreateRequest(name = OTHER_NAME)).id

        val result = runWhileHolding(
            holdingChanges = { connection ->
                connection.prepareStatement("UPDATE categories SET name = ? WHERE id = ?").use {
                    it.setString(1, TARGET_NAME)
                    it.setLong(2, secondId)
                    it.executeUpdate()
                }
            },
            actionName = "카테고리 이름 변경",
            waitTable = "categories"
        ) {
            categoryCommandService.updateCategory(firstId, CategoryUpdateRequest(name = TARGET_NAME))
        }

        assertConflict(result)
        assertThat(nameOf(firstId)).isEqualTo(ORIGINAL_NAME)
        assertThat(nameOf(secondId)).isEqualTo(TARGET_NAME)
    }

    private fun assertConflict(result: Result<Unit>) {
        val thrown = result.exceptionOrNull()
        assertThat(thrown).isInstanceOf(BusinessException::class.java)
        val errorCode = (thrown as BusinessException).errorCode
        assertThat(errorCode.status).isEqualTo(HttpStatus.CONFLICT)
        assertThat(errorCode.code).isEqualTo("CAT002")
    }

    private fun nameOf(categoryId: Long): String =
        jdbcTemplate.queryForObject("SELECT name FROM categories WHERE id = ?", String::class.java, categoryId)!!

    private fun countByName(name: String): Long =
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM categories WHERE name = ?", Long::class.java, name)!!

    companion object {
        private const val ORIGINAL_NAME = "이름변경-원래"
        private const val OTHER_NAME = "이름변경-다른"
        private const val TARGET_NAME = "이름변경-대상"
        private val CATALOG_TABLES = listOf("wishlists", "reviews", "product_statistics", "product_images", "products", "categories")
    }
}
