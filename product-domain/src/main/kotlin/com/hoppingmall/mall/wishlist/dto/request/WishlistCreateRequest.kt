package com.hoppingmall.mall.wishlist.dto.request

import jakarta.validation.constraints.NotNull

data class WishlistCreateRequest(
    @field:NotNull(message = "상품 ID는 필수입니다")
    val productId: Long
)
