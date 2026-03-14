package com.hoppingmall.mall.user.api

interface SellerQueryPort {
    fun findByUserId(userId: Long): SellerInfo?
}

data class SellerInfo(
    val id: Long,
    val userId: Long
)
