package com.hoppingmall.order.refund.dto

import java.math.BigDecimal

interface RefundAggregation {
    fun getProductId(): Long
    fun getTotalQuantity(): Long
    fun getTotalAmount(): BigDecimal
}
