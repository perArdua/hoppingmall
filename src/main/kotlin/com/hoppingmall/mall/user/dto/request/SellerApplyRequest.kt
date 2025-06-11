package com.hoppingmall.mall.user.dto.request

import jakarta.validation.constraints.NotBlank

data class SellerApplyRequest(
    @field:NotBlank(message = "사업자 등록번호는 필수입니다.")
    val businessNumber: String
)
