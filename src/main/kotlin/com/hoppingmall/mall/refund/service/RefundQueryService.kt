package com.hoppingmall.mall.refund.service

import com.hoppingmall.mall.refund.dto.response.RefundResponse
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

interface RefundQueryService {
    fun getRefund(refundId: Long, userId: Long): RefundResponse
    fun getMyRefunds(buyerId: Long, pageable: Pageable): Page<RefundResponse>
    fun getSellerRefunds(sellerId: Long, pageable: Pageable): Page<RefundResponse>
}
