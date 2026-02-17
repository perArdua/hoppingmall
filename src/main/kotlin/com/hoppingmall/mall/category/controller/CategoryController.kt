package com.hoppingmall.mall.category.controller

import com.hoppingmall.mall.category.dto.request.CategoryCreateRequest
import com.hoppingmall.mall.category.dto.request.CategoryUpdateRequest
import com.hoppingmall.mall.category.dto.response.CategoryResponse
import com.hoppingmall.mall.category.service.CategoryCommandService
import com.hoppingmall.mall.category.service.CategoryQueryService
import com.hoppingmall.mall.global.common.response.ApiResponse
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/categories")
class CategoryController(
    private val categoryCommandService: CategoryCommandService,
    private val categoryQueryService: CategoryQueryService
) {

    @PostMapping
    fun createCategory(
        @Valid @RequestBody request: CategoryCreateRequest
    ): ResponseEntity<ApiResponse<CategoryResponse>> {
        val response = categoryCommandService.createCategory(request)
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(response))
    }

    @GetMapping("/{categoryId}")
    fun getCategory(
        @PathVariable categoryId: Long
    ): ResponseEntity<ApiResponse<CategoryResponse>> {
        val response = categoryQueryService.getCategory(categoryId)
        return ResponseEntity.ok(ApiResponse.success(response))
    }

    @GetMapping("/root")
    fun getRootCategories(): ResponseEntity<ApiResponse<List<CategoryResponse>>> {
        val response = categoryQueryService.getRootCategories()
        return ResponseEntity.ok(ApiResponse.success(response))
    }

    @GetMapping("/{categoryId}/sub")
    fun getSubCategories(
        @PathVariable categoryId: Long
    ): ResponseEntity<ApiResponse<List<CategoryResponse>>> {
        val response = categoryQueryService.getSubCategories(categoryId)
        return ResponseEntity.ok(ApiResponse.success(response))
    }

    @PutMapping("/{categoryId}")
    fun updateCategory(
        @PathVariable categoryId: Long,
        @Valid @RequestBody request: CategoryUpdateRequest
    ): ResponseEntity<ApiResponse<CategoryResponse>> {
        val response = categoryCommandService.updateCategory(categoryId, request)
        return ResponseEntity.ok(ApiResponse.success(response))
    }

    @DeleteMapping("/{categoryId}")
    fun deleteCategory(
        @PathVariable categoryId: Long
    ): ResponseEntity<Void> {
        categoryCommandService.deleteCategory(categoryId)
        return ResponseEntity.noContent().build()
    }
}
