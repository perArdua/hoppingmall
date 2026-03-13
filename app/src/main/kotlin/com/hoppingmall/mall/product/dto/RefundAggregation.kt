package com.hoppingmall.mall.product.dto

import java.math.BigDecimal

interface RefundAggregation {
    fun getProductId(): Long
    fun getTotalQuantity(): Long
    fun getTotalAmount(): BigDecimal
}
