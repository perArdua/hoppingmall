package com.hoppingmall.mall.category.service

import com.hoppingmall.mall.category.domain.Category
import com.hoppingmall.mall.category.domain.repository.CategoryRepository
import com.hoppingmall.mall.category.dto.request.CategoryCreateRequest
import com.hoppingmall.mall.category.dto.request.CategoryUpdateRequest
import com.hoppingmall.mall.category.exception.CategoryAlreadyExistsException
import com.hoppingmall.mall.category.exception.CategoryCircularReferenceException
import com.hoppingmall.mall.category.exception.CategoryHasChildrenException
import com.hoppingmall.mall.category.exception.CategoryNotFoundException
import com.hoppingmall.mall.support.fixture.fixture
import com.hoppingmall.mall.support.withId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*

@DisplayName("CategoryCommandServiceImpl")
@DisplayNameGeneration(ReplaceUnderscores::class)
class CategoryCommandServiceImplTest {

    private val categoryRepository: CategoryRepository = mock()
    private val categoryCommandService = CategoryCommandServiceImpl(categoryRepository)

    @Nested
    @DisplayName("createCategory")
    inner class CreateCategory {
        @Test
        fun 루트_카테고리를_생성한다() {
            // given
            val request = CategoryCreateRequest(name = "전자제품")

            whenever(categoryRepository.existsByName("전자제품")).thenReturn(false)
            whenever(categoryRepository.save(any<Category>())).thenAnswer { invocation ->
                invocation.getArgument<Category>(0).withId(1L)
            }

            // when
            val response = categoryCommandService.createCategory(request)

            // then
            assertEquals(1L, response.id)
            assertEquals("전자제품", response.name)
            assertNull(response.parentCategoryId)
            assertEquals(0, response.depth)
        }

        @Test
        fun 하위_카테고리를_생성한다() {
            // given
            val request = CategoryCreateRequest(name = "노트북", parentCategoryId = 1L)
            val parentCategory = Category.fixture(name = "전자제품").withId(1L)

            whenever(categoryRepository.existsByName("노트북")).thenReturn(false)
            whenever(categoryRepository.findNullableById(1L)).thenReturn(parentCategory)
            whenever(categoryRepository.save(any<Category>())).thenAnswer { invocation ->
                invocation.getArgument<Category>(0).withId(2L)
            }

            // when
            val response = categoryCommandService.createCategory(request)

            // then
            assertEquals(2L, response.id)
            assertEquals("노트북", response.name)
            assertEquals(1L, response.parentCategoryId)
            assertEquals(1, response.depth)
        }

        @Test
        fun 이름이_중복되면_예외가_발생한다() {
            // given
            val request = CategoryCreateRequest(name = "전자제품")

            whenever(categoryRepository.existsByName("전자제품")).thenReturn(true)

            // when & then
            assertThrows<CategoryAlreadyExistsException> {
                categoryCommandService.createCategory(request)
            }
        }

        @Test
        fun 조상_체인에_순환_참조가_있으면_예외가_발생한다() {
            // given
            val request = CategoryCreateRequest(name = "새카테고리", parentCategoryId = 3L)

            val catA = Category.fixture(name = "A", parentCategoryId = 3L).withId(1L)
            val catB = Category.fixture(name = "B", parentCategoryId = 1L, depth = 1).withId(2L)
            val catC = Category.fixture(name = "C", parentCategoryId = 2L, depth = 2).withId(3L)

            whenever(categoryRepository.existsByName("새카테고리")).thenReturn(false)
            whenever(categoryRepository.findNullableById(3L)).thenReturn(catC)
            whenever(categoryRepository.findNullableById(2L)).thenReturn(catB)
            whenever(categoryRepository.findNullableById(1L)).thenReturn(catA)

            // when & then
            assertThrows<CategoryCircularReferenceException> {
                categoryCommandService.createCategory(request)
            }
        }

        @Test
        fun 조상_체인_깊이가_상한을_초과하면_예외가_발생한다() {
            // given
            val request = CategoryCreateRequest(name = "깊은카테고리", parentCategoryId = 1L)

            val categories = (1L..12L).map { id ->
                val parentId = if (id < 12L) id + 1 else null
                Category.fixture(name = "Cat$id", parentCategoryId = parentId, depth = id.toInt()).withId(id)
            }

            whenever(categoryRepository.existsByName("깊은카테고리")).thenReturn(false)
            categories.forEach { cat ->
                whenever(categoryRepository.findNullableById(cat.id!!)).thenReturn(cat)
            }

            // when & then
            assertThrows<CategoryCircularReferenceException> {
                categoryCommandService.createCategory(request)
            }
        }

        @Test
        fun 부모_카테고리가_존재하지_않으면_예외가_발생한다() {
            // given
            val request = CategoryCreateRequest(name = "노트북", parentCategoryId = 999L)

            whenever(categoryRepository.existsByName("노트북")).thenReturn(false)
            whenever(categoryRepository.findNullableById(999L)).thenReturn(null)

            // when & then
            assertThrows<CategoryNotFoundException> {
                categoryCommandService.createCategory(request)
            }
        }
    }

    @Nested
    @DisplayName("updateCategory")
    inner class UpdateCategory {
        @Test
        fun 카테고리_이름을_변경한다() {
            // given
            val category = Category.fixture(name = "전자제품").withId(1L)

            whenever(categoryRepository.findNullableById(1L)).thenReturn(category)
            whenever(categoryRepository.existsByNameAndIdNot("가전제품", 1L)).thenReturn(false)

            // when
            val response = categoryCommandService.updateCategory(1L, CategoryUpdateRequest(name = "가전제품"))

            // then
            assertEquals("가전제품", response.name)
        }

        @Test
        fun 카테고리가_존재하지_않으면_예외가_발생한다() {
            // given
            whenever(categoryRepository.findNullableById(999L)).thenReturn(null)

            // when & then
            assertThrows<CategoryNotFoundException> {
                categoryCommandService.updateCategory(999L, CategoryUpdateRequest(name = "가전제품"))
            }
        }

        @Test
        fun 변경하려는_이름이_이미_존재하면_예외가_발생한다() {
            // given
            val category = Category.fixture(name = "전자제품").withId(1L)

            whenever(categoryRepository.findNullableById(1L)).thenReturn(category)
            whenever(categoryRepository.existsByNameAndIdNot("의류", 1L)).thenReturn(true)

            // when & then
            assertThrows<CategoryAlreadyExistsException> {
                categoryCommandService.updateCategory(1L, CategoryUpdateRequest(name = "의류"))
            }
        }
    }

    @Nested
    @DisplayName("deleteCategory")
    inner class DeleteCategory {
        @Test
        fun 카테고리를_삭제한다() {
            // given
            val category = Category.fixture(name = "전자제품").withId(1L)

            whenever(categoryRepository.findNullableById(1L)).thenReturn(category)
            whenever(categoryRepository.existsByParentCategoryId(1L)).thenReturn(false)

            // when
            categoryCommandService.deleteCategory(1L)

            // then
            assertThat(category.deletedAt).isNotNull()
        }

        @Test
        fun 카테고리가_존재하지_않으면_예외가_발생한다() {
            // given
            whenever(categoryRepository.findNullableById(999L)).thenReturn(null)

            // when & then
            assertThrows<CategoryNotFoundException> {
                categoryCommandService.deleteCategory(999L)
            }
        }

        @Test
        fun 하위_카테고리가_존재하면_예외가_발생한다() {
            // given
            val category = Category.fixture(name = "전자제품").withId(1L)

            whenever(categoryRepository.findNullableById(1L)).thenReturn(category)
            whenever(categoryRepository.existsByParentCategoryId(1L)).thenReturn(true)

            // when & then
            assertThrows<CategoryHasChildrenException> {
                categoryCommandService.deleteCategory(1L)
            }
        }
    }
}
