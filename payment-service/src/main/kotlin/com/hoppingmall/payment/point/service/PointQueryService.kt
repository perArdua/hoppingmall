package com.hoppingmall.payment.point.service

import com.hoppingmall.payment.point.dto.response.PointBalanceResponse
import com.hoppingmall.payment.point.dto.response.PointHistoryResponse
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

interface PointQueryService {
    fun getPointBalance(userId: Long): PointBalanceResponse

    fun getPointHistory(userId: Long, pageable: Pageable): Page<PointHistoryResponse>
}
