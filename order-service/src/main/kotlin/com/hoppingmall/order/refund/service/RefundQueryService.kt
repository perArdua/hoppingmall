package com.hoppingmall.order.refund.service

import com.hoppingmall.order.refund.dto.response.RefundResponse
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice

interface RefundQueryService {
    fun getRefund(refundId: Long, userId: Long): RefundResponse
    fun getMyRefunds(buyerId: Long, pageable: Pageable): Slice<RefundResponse>
    fun getSellerRefunds(sellerId: Long, pageable: Pageable): Slice<RefundResponse>
}
