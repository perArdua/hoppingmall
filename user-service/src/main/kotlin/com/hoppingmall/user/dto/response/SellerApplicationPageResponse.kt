package com.hoppingmall.user.dto.response

data class SellerApplicationPageResponse(
    val content: List<AdminSellerApplicationResponse>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int
)
