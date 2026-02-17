package com.hoppingmall.mall.product.service

import java.math.BigDecimal

interface ProductStatisticsCommandService {
    fun incrementSalesStats(productId: Long, quantity: Long, amount: BigDecimal)
    fun decrementSalesStats(productId: Long, quantity: Long, amount: BigDecimal)
    fun incrementRefundStats(productId: Long, quantity: Long, amount: BigDecimal)
    fun flushDailySnapshot()
}
