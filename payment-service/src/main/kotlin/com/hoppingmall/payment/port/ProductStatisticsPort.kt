package com.hoppingmall.payment.port

import java.math.BigDecimal

interface ProductStatisticsPort {
    fun incrementRefundStats(productId: Long, quantity: Long, amount: BigDecimal)

    fun incrementRefundStats(productId: Long, quantity: Long, amount: BigDecimal, requestKey: String)

    fun incrementRefundStats(productId: Long, quantity: Long, amount: BigDecimal, productPrice: BigDecimal)

    fun incrementRefundStats(
        productId: Long,
        quantity: Long,
        amount: BigDecimal,
        productPrice: BigDecimal,
        requestKey: String
    )
}
