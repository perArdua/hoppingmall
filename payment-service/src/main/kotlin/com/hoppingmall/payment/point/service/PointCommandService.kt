package com.hoppingmall.payment.point.service

import java.math.BigDecimal

interface PointCommandService {
    fun refundPoints(userId: Long, amount: BigDecimal, paymentId: Long, orderId: Long, refundId: Long)
    fun useForPayment(userId: Long, paymentId: Long, orderId: Long, amount: BigDecimal)
    fun restoreUsedPoints(userId: Long, paymentId: Long, orderId: Long, eventId: String, reason: String): BigDecimal
}
