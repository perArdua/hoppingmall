package com.hoppingmall.mall.order.api

interface CartAggregation {
    fun getProductId(): Long
    fun getBuyerCount(): Long
}
