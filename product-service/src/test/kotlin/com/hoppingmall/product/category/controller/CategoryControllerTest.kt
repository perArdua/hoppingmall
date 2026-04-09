package com.hoppingmall.product.category.controller

import com.hoppingmall.product.category.dto.response.CategoryResponse
import com.hoppingmall.product.category.exception.CategoryNotFoundException
import com.hoppingmall.product.category.service.CategoryCommandService
import com.hoppingmall.product.category.service.CategoryQueryService
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
import org.springframework.http.HttpStatus
import java.time.LocalDateTime

@DisplayName("CategoryController")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class CategoryControllerTest {

    @Mock
    private lateinit var categoryCommandService: CategoryCommandService

    @Mock
    private lateinit var categoryQueryService: CategoryQueryService

    @InjectMocks
    private lateinit var controller: CategoryController

    private fun categoryResponse(id: Long = 1L) = CategoryResponse(
        id = id,
        name = "전자기기",
        parentCategoryId = null,
        depth = 0,
        createdAt = LocalDateTime.now(),
        updatedAt = null
    )

    @Test
    fun 카테고리를_생성한다() {
        whenever(categoryCommandService.createCategory(any())).thenReturn(categoryResponse())

        val result = controller.createCategory(
            com.hoppingmall.product.category.dto.request.CategoryCreateRequest(name = "전자기기", parentCategoryId = null)
        )

        assertThat(result.statusCode).isEqualTo(HttpStatus.CREATED)
        assertThat(result.body!!.data!!.name).isEqualTo("전자기기")
    }

    @Test
    fun 카테고리를_단건_조회한다() {
        whenever(categoryQueryService.getCategory(1L)).thenReturn(categoryResponse())

        val result = controller.getCategory(1L)

        assertThat(result.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(result.body!!.data!!.name).isEqualTo("전자기기")
    }

    @Test
    fun 존재하지_않는_카테고리_조회_시_예외를_발생시킨다() {
        whenever(categoryQueryService.getCategory(999L)).thenReturn(null)

        assertThatThrownBy { controller.getCategory(999L) }
            .isInstanceOf(CategoryNotFoundException::class.java)
    }

    @Test
    fun 루트_카테고리_목록을_조회한다() {
        whenever(categoryQueryService.getRootCategories()).thenReturn(listOf(categoryResponse()))

        val result = controller.getRootCategories()

        assertThat(result.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(result.body!!.data).hasSize(1)
    }

    @Test
    fun 하위_카테고리_목록을_조회한다() {
        whenever(categoryQueryService.getSubCategories(1L)).thenReturn(listOf(categoryResponse(2L)))

        val result = controller.getSubCategories(1L)

        assertThat(result.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(result.body!!.data).hasSize(1)
    }

    @Test
    fun 카테고리를_수정한다() {
        whenever(categoryCommandService.updateCategory(any(), any())).thenReturn(categoryResponse())

        val result = controller.updateCategory(
            1L,
            com.hoppingmall.product.category.dto.request.CategoryUpdateRequest(name = "가전제품")
        )

        assertThat(result.statusCode).isEqualTo(HttpStatus.OK)
    }

    @Test
    fun 카테고리를_삭제한다() {
        val result = controller.deleteCategory(1L)

        assertThat(result.statusCode).isEqualTo(HttpStatus.NO_CONTENT)
        verify(categoryCommandService).deleteCategory(1L)
    }
}
