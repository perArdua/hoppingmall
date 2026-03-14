package com.hoppingmall.mall.order.api

import java.math.BigDecimal

interface SalesAggregation {
    fun getProductId(): Long
    fun getTotalQuantity(): Long
    fun getTotalAmount(): BigDecimal
}
