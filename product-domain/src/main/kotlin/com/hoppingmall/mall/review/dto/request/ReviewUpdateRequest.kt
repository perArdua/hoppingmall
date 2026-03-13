package com.hoppingmall.mall.review.dto.request

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

data class ReviewUpdateRequest(
    @field:NotNull(message = "평점은 필수입니다.")
    @field:Min(value = 1, message = "평점은 1 이상이어야 합니다.")
    @field:Max(value = 5, message = "평점은 5 이하여야 합니다.")
    val rating: Int,

    @field:NotBlank(message = "리뷰 내용은 필수입니다.")
    @field:Size(min = 10, max = 2000, message = "리뷰 내용은 10자 이상 2000자 이하여야 합니다.")
    val content: String,

    val imageUrl: String? = null
)
