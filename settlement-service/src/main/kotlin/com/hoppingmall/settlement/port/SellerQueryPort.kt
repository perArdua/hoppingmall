package com.hoppingmall.settlement.port

interface SellerQueryPort {
    fun findByUserId(userId: Long): SellerInfo?
}

data class SellerInfo(
    val id: Long,
    val userId: Long
)
