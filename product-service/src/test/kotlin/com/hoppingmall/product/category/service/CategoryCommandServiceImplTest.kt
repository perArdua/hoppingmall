package com.hoppingmall.product.category.service

import com.hoppingmall.product.category.domain.Category
import com.hoppingmall.product.category.domain.repository.CategoryRepository
import com.hoppingmall.product.category.dto.request.CategoryCreateRequest
import com.hoppingmall.product.category.dto.request.CategoryUpdateRequest
import com.hoppingmall.product.category.exception.CategoryAlreadyExistsException
import com.hoppingmall.product.category.exception.CategoryCircularReferenceException
import com.hoppingmall.product.category.exception.CategoryHasChildrenException
import com.hoppingmall.product.category.exception.CategoryNotFoundException
import com.hoppingmall.product.support.withId
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@DisplayName("CategoryCommandServiceImpl")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class CategoryCommandServiceImplTest {

    @Mock
    private lateinit var categoryRepository: CategoryRepository

    @InjectMocks
    private lateinit var service: CategoryCommandServiceImpl

    @Test
    fun 루트_카테고리를_생성한다() {
        val request = CategoryCreateRequest(name = "전자기기", parentCategoryId = null)
        val saved = Category.create(name = "전자기기", parentCategoryId = null, depth = 0).withId(1L)

        whenever(categoryRepository.existsByName("전자기기")).thenReturn(false)
        whenever(categoryRepository.save(any<Category>())).thenReturn(saved)

        val result = service.createCategory(request)

        assertThat(result.name).isEqualTo("전자기기")
        assertThat(result.depth).isEqualTo(0)
    }

    @Test
    fun 하위_카테고리를_생성한다() {
        val parent = Category.create(name = "전자기기", parentCategoryId = null, depth = 0).withId(1L)
        val request = CategoryCreateRequest(name = "노트북", parentCategoryId = 1L)
        val saved = Category.create(name = "노트북", parentCategoryId = 1L, depth = 1).withId(2L)

        whenever(categoryRepository.existsByName("노트북")).thenReturn(false)
        whenever(categoryRepository.findNullableById(1L)).thenReturn(parent)
        whenever(categoryRepository.save(any<Category>())).thenReturn(saved)

        val result = service.createCategory(request)

        assertThat(result.name).isEqualTo("노트북")
        assertThat(result.depth).isEqualTo(1)
    }

    @Test
    fun 이미_존재하는_이름으로_카테고리_생성_시_예외를_발생시킨다() {
        val request = CategoryCreateRequest(name = "전자기기", parentCategoryId = null)

        whenever(categoryRepository.existsByName("전자기기")).thenReturn(true)

        assertThatThrownBy { service.createCategory(request) }
            .isInstanceOf(CategoryAlreadyExistsException::class.java)
    }

    @Test
    fun 존재하지_않는_부모_카테고리로_생성_시_예외를_발생시킨다() {
        val request = CategoryCreateRequest(name = "노트북", parentCategoryId = 999L)

        whenever(categoryRepository.existsByName("노트북")).thenReturn(false)
        whenever(categoryRepository.findNullableById(999L)).thenReturn(null)

        assertThatThrownBy { service.createCategory(request) }
            .isInstanceOf(CategoryNotFoundException::class.java)
    }

    @Test
    fun 카테고리를_수정한다() {
        val category = Category.create(name = "전자기기", parentCategoryId = null, depth = 0).withId(1L)
        val request = CategoryUpdateRequest(name = "가전제품")

        whenever(categoryRepository.findNullableById(1L)).thenReturn(category)
        whenever(categoryRepository.existsByNameAndIdNot("가전제품", 1L)).thenReturn(false)

        val result = service.updateCategory(1L, request)

        assertThat(result.name).isEqualTo("가전제품")
    }

    @Test
    fun 존재하지_않는_카테고리_수정_시_예외를_발생시킨다() {
        val request = CategoryUpdateRequest(name = "가전제품")

        whenever(categoryRepository.findNullableById(999L)).thenReturn(null)

        assertThatThrownBy { service.updateCategory(999L, request) }
            .isInstanceOf(CategoryNotFoundException::class.java)
    }

    @Test
    fun 중복_이름으로_카테고리_수정_시_예외를_발생시킨다() {
        val category = Category.create(name = "전자기기", parentCategoryId = null, depth = 0).withId(1L)
        val request = CategoryUpdateRequest(name = "가전제품")

        whenever(categoryRepository.findNullableById(1L)).thenReturn(category)
        whenever(categoryRepository.existsByNameAndIdNot("가전제품", 1L)).thenReturn(true)

        assertThatThrownBy { service.updateCategory(1L, request) }
            .isInstanceOf(CategoryAlreadyExistsException::class.java)
    }

    @Test
    fun 카테고리를_삭제한다() {
        val category = Category.create(name = "전자기기", parentCategoryId = null, depth = 0).withId(1L)

        whenever(categoryRepository.findNullableById(1L)).thenReturn(category)
        whenever(categoryRepository.existsByParentCategoryId(1L)).thenReturn(false)

        service.deleteCategory(1L)

        assertThat(category.deletedAt).isNotNull()
    }

    @Test
    fun 존재하지_않는_카테고리_삭제_시_예외를_발생시킨다() {
        whenever(categoryRepository.findNullableById(999L)).thenReturn(null)

        assertThatThrownBy { service.deleteCategory(999L) }
            .isInstanceOf(CategoryNotFoundException::class.java)
    }

    @Test
    fun 하위_카테고리가_있는_카테고리_삭제_시_예외를_발생시킨다() {
        val category = Category.create(name = "전자기기", parentCategoryId = null, depth = 0).withId(1L)

        whenever(categoryRepository.findNullableById(1L)).thenReturn(category)
        whenever(categoryRepository.existsByParentCategoryId(1L)).thenReturn(true)

        assertThatThrownBy { service.deleteCategory(1L) }
            .isInstanceOf(CategoryHasChildrenException::class.java)
    }

    @Test
    fun 순환_참조가_있는_부모_카테고리로_생성_시_예외를_발생시킨다() {
        val catA = Category.create(name = "A", parentCategoryId = 2L, depth = 1).withId(1L)
        val catB = Category.create(name = "B", parentCategoryId = 1L, depth = 1).withId(2L)
        val request = CategoryCreateRequest(name = "C", parentCategoryId = 1L)

        whenever(categoryRepository.existsByName("C")).thenReturn(false)
        whenever(categoryRepository.findNullableById(1L)).thenReturn(catA)
        whenever(categoryRepository.findNullableById(2L)).thenReturn(catB)

        assertThatThrownBy { service.createCategory(request) }
            .isInstanceOf(CategoryCircularReferenceException::class.java)
    }
}
