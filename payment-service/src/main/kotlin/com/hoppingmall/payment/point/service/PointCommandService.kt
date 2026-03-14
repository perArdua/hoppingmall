package com.hoppingmall.payment.point.service

import com.hoppingmall.payment.point.dto.request.PointUseRequest
import com.hoppingmall.payment.point.dto.response.PointUseResponse
import java.math.BigDecimal

interface PointCommandService {
    fun usePoint(userId: Long, request: PointUseRequest): PointUseResponse
    fun refundPoints(userId: Long, amount: BigDecimal, paymentId: Long, orderId: Long)
}
