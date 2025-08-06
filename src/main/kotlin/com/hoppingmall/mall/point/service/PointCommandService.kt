package com.hoppingmall.mall.point.service

import com.hoppingmall.mall.point.dto.request.PointUseRequest
import com.hoppingmall.mall.point.dto.response.PointUseResponse

interface PointCommandService {
    fun usePoint(userId: Long, request: PointUseRequest): PointUseResponse
} 