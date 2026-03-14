package com.hoppingmall.mall.order.api

import java.math.BigDecimal

interface RefundAggregation {
    fun getProductId(): Long
    fun getTotalQuantity(): Long
    fun getTotalAmount(): BigDecimal
}
