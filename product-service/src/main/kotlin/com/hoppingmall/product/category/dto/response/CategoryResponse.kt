package com.hoppingmall.product.category.dto.response

import com.hoppingmall.product.category.domain.Category
import java.time.LocalDateTime

data class CategoryResponse(
    val id: Long,
    val name: String,
    val parentCategoryId: Long?,
    val depth: Int,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime?
) {
    companion object {
        fun from(category: Category): CategoryResponse {
            return CategoryResponse(
                id = category.id!!,
                name = category.name,
                parentCategoryId = category.parentCategoryId,
                depth = category.depth,
                createdAt = category.createdAt,
                updatedAt = category.updatedAt
            )
        }
    }
}
