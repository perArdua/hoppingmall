package com.hoppingmall.product.product.dto

import java.math.BigDecimal

interface SalesAggregation {
    fun getProductId(): Long
    fun getTotalQuantity(): Long
    fun getTotalAmount(): BigDecimal
}
