package com.hoppingmall.product.product.dto

import java.math.BigDecimal

interface RefundAggregation {
    fun getProductId(): Long
    fun getTotalQuantity(): Long
    fun getTotalAmount(): BigDecimal
}
