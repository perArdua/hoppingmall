package com.hoppingmall.product.category.dto.request

import jakarta.validation.constraints.NotBlank

data class CategoryUpdateRequest(
    @field:NotBlank(message = "카테고리 이름은 필수입니다")
    val name: String
)
