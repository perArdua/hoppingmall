package com.hoppingmall.payment.port

import java.math.BigDecimal

interface ProductStatisticsPort {
    fun incrementRefundStats(productId: Long, quantity: Long, amount: BigDecimal)
}
