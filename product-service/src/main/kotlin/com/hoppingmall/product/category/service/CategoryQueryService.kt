package com.hoppingmall.product.category.service

import com.hoppingmall.product.category.dto.response.CategoryResponse

interface CategoryQueryService {
    fun getCategory(categoryId: Long): CategoryResponse?
    fun getRootCategories(): List<CategoryResponse>
    fun getSubCategories(parentCategoryId: Long): List<CategoryResponse>
}
