package com.hoppingmall.order.refund.service

import com.hoppingmall.order.refund.dto.request.RefundApprovalRequest
import com.hoppingmall.order.refund.dto.request.RefundCreateRequest
import com.hoppingmall.order.refund.dto.response.RefundResponse

interface RefundCommandService {
    fun requestRefund(buyerId: Long, request: RefundCreateRequest): RefundResponse
    fun approveRefund(refundId: Long, approverId: Long): RefundResponse
    fun rejectRefund(refundId: Long, approverId: Long, request: RefundApprovalRequest): RefundResponse
}
