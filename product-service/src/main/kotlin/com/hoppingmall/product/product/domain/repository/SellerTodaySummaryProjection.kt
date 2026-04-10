package com.hoppingmall.product.product.domain.repository

import java.math.BigDecimal

interface SellerTodaySummaryProjection {
    fun getTotalProducts(): Long
    fun getTodaySalesAmount(): BigDecimal
    fun getTodayOrderCount(): Long
    fun getTodayRefundAmount(): BigDecimal
}
