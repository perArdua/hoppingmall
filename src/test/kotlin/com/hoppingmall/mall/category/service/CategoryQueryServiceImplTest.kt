package com.hoppingmall.mall.category.service

import com.hoppingmall.mall.category.domain.Category
import com.hoppingmall.mall.category.domain.repository.CategoryRepository
import com.hoppingmall.mall.category.exception.CategoryNotFoundException
import com.hoppingmall.mall.support.fixture.fixture
import com.hoppingmall.mall.support.withId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@DisplayName("CategoryQueryServiceImpl")
@DisplayNameGeneration(ReplaceUnderscores::class)
class CategoryQueryServiceImplTest {

    private val categoryRepository: CategoryRepository = mock()
    private val categoryQueryService = CategoryQueryServiceImpl(categoryRepository)

    @Nested
    @DisplayName("getCategory")
    inner class GetCategory {
        @Test
        fun 카테고리를_조회한다() {
            // given
            val category = Category.fixture(name = "전자제품").withId(1L)

            whenever(categoryRepository.findNullableById(1L)).thenReturn(category)

            // when
            val response = categoryQueryService.getCategory(1L)

            // then
            assertEquals(1L, response.id)
            assertEquals("전자제품", response.name)
        }

        @Test
        fun 카테고리가_존재하지_않으면_예외가_발생한다() {
            // given
            whenever(categoryRepository.findNullableById(999L)).thenReturn(null)

            // when & then
            assertThrows<CategoryNotFoundException> {
                categoryQueryService.getCategory(999L)
            }
        }
    }

    @Nested
    @DisplayName("getRootCategories")
    inner class GetRootCategories {
        @Test
        fun 루트_카테고리_목록을_조회한다() {
            // given
            val categories = listOf(
                Category.fixture(name = "전자제품").withId(1L),
                Category.fixture(name = "의류").withId(2L)
            )

            whenever(categoryRepository.findByParentCategoryIdIsNull()).thenReturn(categories)

            // when
            val response = categoryQueryService.getRootCategories()

            // then
            assertEquals(2, response.size)
            assertEquals("전자제품", response[0].name)
            assertEquals("의류", response[1].name)
        }
    }

    @Nested
    @DisplayName("getSubCategories")
    inner class GetSubCategories {
        @Test
        fun 하위_카테고리_목록을_조회한다() {
            // given
            val subCategories = listOf(
                Category.fixture(name = "노트북", parentCategoryId = 1L, depth = 1).withId(2L),
                Category.fixture(name = "스마트폰", parentCategoryId = 1L, depth = 1).withId(3L)
            )

            whenever(categoryRepository.findByParentCategoryId(1L)).thenReturn(subCategories)

            // when
            val response = categoryQueryService.getSubCategories(1L)

            // then
            assertEquals(2, response.size)
            assertEquals("노트북", response[0].name)
            assertEquals("스마트폰", response[1].name)
        }
    }
}
