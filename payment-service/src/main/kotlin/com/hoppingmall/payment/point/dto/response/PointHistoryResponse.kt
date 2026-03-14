package com.hoppingmall.payment.point.dto.response

import com.hoppingmall.payment.point.enum.PointType
import java.math.BigDecimal
import java.time.LocalDateTime

data class PointHistoryResponse(
    val id: Long,
    val userId: Long,
    val amount: BigDecimal,
    val type: PointType,
    val reason: String?,
    val orderId: Long?,
    val paymentId: Long?,
    val createdAt: LocalDateTime
)
