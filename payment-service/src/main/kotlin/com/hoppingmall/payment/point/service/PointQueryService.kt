package com.hoppingmall.payment.point.service

import com.hoppingmall.payment.point.dto.response.PointBalanceResponse
import com.hoppingmall.payment.point.dto.response.PointHistoryResponse
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice

interface PointQueryService {
    fun getPointBalance(userId: Long): PointBalanceResponse

    fun getPointHistory(userId: Long, pageable: Pageable): Slice<PointHistoryResponse>
}
