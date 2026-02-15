package com.hoppingmall.mall.payment.dto.event

import java.math.BigDecimal

data class PointEarnRequestEvent(
    val eventId: String,
    val userId: Long,
    val orderId: Long,
    val paymentId: Long,
    val earnAmount: BigDecimal,
    val reason: String = "결제 완료"
) 
