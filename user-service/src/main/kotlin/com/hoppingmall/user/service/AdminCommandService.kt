package com.hoppingmall.user.service

import com.hoppingmall.user.dto.request.SellerApprovalRequest

interface AdminCommandService {
    fun updateSellerApprovalStatus(sellerId: Long, request: SellerApprovalRequest)
}
