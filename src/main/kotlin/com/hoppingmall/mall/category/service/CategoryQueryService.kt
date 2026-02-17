package com.hoppingmall.mall.category.service

import com.hoppingmall.mall.category.dto.response.CategoryResponse

interface CategoryQueryService {
    fun getCategory(categoryId: Long): CategoryResponse
    fun getRootCategories(): List<CategoryResponse>
    fun getSubCategories(parentCategoryId: Long): List<CategoryResponse>
}
