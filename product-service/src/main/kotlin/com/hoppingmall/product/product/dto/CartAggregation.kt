package com.hoppingmall.product.product.dto

interface CartAggregation {
    fun getProductId(): Long
    fun getBuyerCount(): Long
}
