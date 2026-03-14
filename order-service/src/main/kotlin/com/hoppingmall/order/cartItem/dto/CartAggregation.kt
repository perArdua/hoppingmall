package com.hoppingmall.order.cartItem.dto

interface CartAggregation {
    fun getProductId(): Long
    fun getBuyerCount(): Long
}
