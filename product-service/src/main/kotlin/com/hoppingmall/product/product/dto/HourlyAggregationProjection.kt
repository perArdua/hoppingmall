package com.hoppingmall.product.product.dto

import java.math.BigDecimal

interface HourlyAggregationProjection {
    fun getHour(): Int
    fun getTotalAmount(): BigDecimal
    fun getTotalOrders(): Long
}
