package com.hoppingmall.mall.point.service

import com.hoppingmall.mall.point.dto.request.PointUseRequest
import com.hoppingmall.mall.point.dto.response.PointUseResponse
import java.math.BigDecimal

interface PointCommandService {
    fun usePoint(userId: Long, request: PointUseRequest): PointUseResponse
    fun refundPoints(userId: Long, amount: BigDecimal, paymentId: Long, orderId: Long)
}