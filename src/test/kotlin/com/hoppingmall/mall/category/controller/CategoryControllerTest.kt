package com.hoppingmall.mall.category.controller

import com.hoppingmall.mall.category.dto.request.CategoryCreateRequest
import com.hoppingmall.mall.category.dto.request.CategoryUpdateRequest
import com.hoppingmall.mall.category.dto.response.CategoryResponse
import com.hoppingmall.mall.category.service.CategoryCommandService
import com.hoppingmall.mall.category.service.CategoryQueryService
import com.hoppingmall.mall.global.common.response.ApiResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import java.time.LocalDateTime

@DisplayName("CategoryController")
@DisplayNameGeneration(ReplaceUnderscores::class)
class CategoryControllerTest {

    private val categoryCommandService: CategoryCommandService = mock()
    private val categoryQueryService: CategoryQueryService = mock()
    private val controller = CategoryController(categoryCommandService, categoryQueryService)

    private val now = LocalDateTime.of(2026, 1, 1, 0, 0, 0)

    @Nested
    @DisplayName("createCategory")
    inner class CreateCategory {
        @Test
        fun 카테고리_생성_성공() {
            // given
            val request = CategoryCreateRequest(name = "전자제품")
            val expectedResponse = CategoryResponse(
                id = 1L, name = "전자제품", parentCategoryId = null, depth = 0,
                createdAt = now, updatedAt = null
            )

            whenever(categoryCommandService.createCategory(request)).thenReturn(expectedResponse)

            // when
            val response: ResponseEntity<ApiResponse<CategoryResponse>> = controller.createCategory(request)

            // then
            assertEquals(HttpStatus.CREATED, response.statusCode)
            assertEquals("SUCCESS", response.body?.code)
            assertEquals(expectedResponse, response.body?.data)
            verify(categoryCommandService).createCategory(request)
        }
    }

    @Nested
    @DisplayName("getCategory")
    inner class GetCategory {
        @Test
        fun 카테고리_조회_성공() {
            // given
            val expectedResponse = CategoryResponse(
                id = 1L, name = "전자제품", parentCategoryId = null, depth = 0,
                createdAt = now, updatedAt = null
            )

            whenever(categoryQueryService.getCategory(1L)).thenReturn(expectedResponse)

            // when
            val response: ResponseEntity<ApiResponse<CategoryResponse>> = controller.getCategory(1L)

            // then
            assertEquals(HttpStatus.OK, response.statusCode)
            assertEquals("SUCCESS", response.body?.code)
            assertEquals(expectedResponse, response.body?.data)
            verify(categoryQueryService).getCategory(1L)
        }
    }

    @Nested
    @DisplayName("getRootCategories")
    inner class GetRootCategories {
        @Test
        fun 루트_카테고리_목록_조회_성공() {
            // given
            val expectedResponse = listOf(
                CategoryResponse(
                    id = 1L, name = "전자제품", parentCategoryId = null, depth = 0,
                    createdAt = now, updatedAt = null
                )
            )

            whenever(categoryQueryService.getRootCategories()).thenReturn(expectedResponse)

            // when
            val response: ResponseEntity<ApiResponse<List<CategoryResponse>>> = controller.getRootCategories()

            // then
            assertEquals(HttpStatus.OK, response.statusCode)
            assertEquals("SUCCESS", response.body?.code)
            assertEquals(1, response.body?.data?.size)
            verify(categoryQueryService).getRootCategories()
        }
    }

    @Nested
    @DisplayName("getSubCategories")
    inner class GetSubCategories {
        @Test
        fun 하위_카테고리_목록_조회_성공() {
            // given
            val expectedResponse = listOf(
                CategoryResponse(
                    id = 2L, name = "노트북", parentCategoryId = 1L, depth = 1,
                    createdAt = now, updatedAt = null
                )
            )

            whenever(categoryQueryService.getSubCategories(1L)).thenReturn(expectedResponse)

            // when
            val response: ResponseEntity<ApiResponse<List<CategoryResponse>>> = controller.getSubCategories(1L)

            // then
            assertEquals(HttpStatus.OK, response.statusCode)
            assertEquals("SUCCESS", response.body?.code)
            assertEquals(1, response.body?.data?.size)
            verify(categoryQueryService).getSubCategories(1L)
        }
    }

    @Nested
    @DisplayName("updateCategory")
    inner class UpdateCategory {
        @Test
        fun 카테고리_수정_성공() {
            // given
            val request = CategoryUpdateRequest(name = "가전제품")
            val expectedResponse = CategoryResponse(
                id = 1L, name = "가전제품", parentCategoryId = null, depth = 0,
                createdAt = now, updatedAt = now
            )

            whenever(categoryCommandService.updateCategory(1L, request)).thenReturn(expectedResponse)

            // when
            val response: ResponseEntity<ApiResponse<CategoryResponse>> = controller.updateCategory(1L, request)

            // then
            assertEquals(HttpStatus.OK, response.statusCode)
            assertEquals("SUCCESS", response.body?.code)
            assertEquals(expectedResponse, response.body?.data)
            verify(categoryCommandService).updateCategory(1L, request)
        }
    }

    @Nested
    @DisplayName("deleteCategory")
    inner class DeleteCategory {
        @Test
        fun 카테고리_삭제_성공() {
            // when
            val response: ResponseEntity<Void> = controller.deleteCategory(1L)

            // then
            assertEquals(HttpStatus.NO_CONTENT, response.statusCode)
            verify(categoryCommandService).deleteCategory(1L)
        }
    }
}
