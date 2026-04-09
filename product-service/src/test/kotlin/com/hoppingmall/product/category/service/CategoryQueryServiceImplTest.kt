package com.hoppingmall.product.category.service

import com.hoppingmall.product.category.domain.Category
import com.hoppingmall.product.category.domain.repository.CategoryRepository
import com.hoppingmall.product.support.withId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever

@DisplayName("CategoryQueryServiceImpl")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class CategoryQueryServiceImplTest {

    @Mock
    private lateinit var categoryRepository: CategoryRepository

    @InjectMocks
    private lateinit var service: CategoryQueryServiceImpl

    @Test
    fun 카테고리를_조회한다() {
        val category = Category.create(name = "전자기기", parentCategoryId = null, depth = 0).withId(1L)

        whenever(categoryRepository.findNullableById(1L)).thenReturn(category)

        val result = service.getCategory(1L)

        assertThat(result).isNotNull
        assertThat(result!!.name).isEqualTo("전자기기")
    }

    @Test
    fun 존재하지_않는_카테고리_조회_시_null을_반환한다() {
        whenever(categoryRepository.findNullableById(999L)).thenReturn(null)

        val result = service.getCategory(999L)

        assertThat(result).isNull()
    }

    @Test
    fun 루트_카테고리_목록을_조회한다() {
        val categories = listOf(
            Category.create(name = "전자기기", parentCategoryId = null, depth = 0).withId(1L),
            Category.create(name = "의류", parentCategoryId = null, depth = 0).withId(2L)
        )

        whenever(categoryRepository.findByParentCategoryIdIsNull()).thenReturn(categories)

        val result = service.getRootCategories()

        assertThat(result).hasSize(2)
    }

    @Test
    fun 하위_카테고리_목록을_조회한다() {
        val categories = listOf(
            Category.create(name = "노트북", parentCategoryId = 1L, depth = 1).withId(2L),
            Category.create(name = "스마트폰", parentCategoryId = 1L, depth = 1).withId(3L)
        )

        whenever(categoryRepository.findByParentCategoryId(1L)).thenReturn(categories)

        val result = service.getSubCategories(1L)

        assertThat(result).hasSize(2)
    }
}
