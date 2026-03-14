package com.hoppingmall.mall.category.dto.request

import jakarta.validation.constraints.NotBlank

data class CategoryCreateRequest(
    @field:NotBlank(message = "카테고리 이름은 필수입니다")
    val name: String,

    val parentCategoryId: Long? = null
)
