package com.hoppingmall.mall.refund.service

import com.hoppingmall.mall.refund.dto.request.RefundApprovalRequest
import com.hoppingmall.mall.refund.dto.request.RefundCreateRequest
import com.hoppingmall.mall.refund.dto.response.RefundResponse

interface RefundCommandService {
    fun requestRefund(buyerId: Long, request: RefundCreateRequest): RefundResponse
    fun approveRefund(refundId: Long, approverId: Long): RefundResponse
    fun rejectRefund(refundId: Long, approverId: Long, request: RefundApprovalRequest): RefundResponse
}
