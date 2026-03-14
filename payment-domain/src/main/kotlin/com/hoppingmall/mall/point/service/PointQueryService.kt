package com.hoppingmall.mall.point.service

import com.hoppingmall.mall.point.dto.response.PointBalanceResponse
import com.hoppingmall.mall.point.dto.response.PointHistoryResponse
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

interface PointQueryService {
    fun getPointBalance(userId: Long): PointBalanceResponse
    
    fun getPointHistory(userId: Long, pageable: Pageable): Page<PointHistoryResponse>
} 