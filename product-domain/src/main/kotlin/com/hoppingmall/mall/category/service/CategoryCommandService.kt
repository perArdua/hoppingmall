package com.hoppingmall.mall.category.service

import com.hoppingmall.mall.category.dto.request.CategoryCreateRequest
import com.hoppingmall.mall.category.dto.request.CategoryUpdateRequest
import com.hoppingmall.mall.category.dto.response.CategoryResponse

interface CategoryCommandService {
    fun createCategory(request: CategoryCreateRequest): CategoryResponse
    fun updateCategory(categoryId: Long, request: CategoryUpdateRequest): CategoryResponse
    fun deleteCategory(categoryId: Long)
}
