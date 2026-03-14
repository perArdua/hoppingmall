package com.hoppingmall.product.category.service

import com.hoppingmall.product.category.dto.request.CategoryCreateRequest
import com.hoppingmall.product.category.dto.request.CategoryUpdateRequest
import com.hoppingmall.product.category.dto.response.CategoryResponse

interface CategoryCommandService {
    fun createCategory(request: CategoryCreateRequest): CategoryResponse
    fun updateCategory(categoryId: Long, request: CategoryUpdateRequest): CategoryResponse
    fun deleteCategory(categoryId: Long)
}
