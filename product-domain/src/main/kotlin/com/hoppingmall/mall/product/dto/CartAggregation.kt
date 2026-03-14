package com.hoppingmall.mall.product.dto

interface CartAggregation {
    fun getProductId(): Long
    fun getBuyerCount(): Long
}
