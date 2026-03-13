package com.hoppingmall.mall.product.api

import java.math.BigDecimal

data class ProductInfo(
    val id: Long,
    val name: String,
    val price: BigDecimal,
    val sellerId: Long,
    val status: String
)
