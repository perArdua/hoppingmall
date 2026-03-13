package com.hoppingmall.mall.user.service.admin

import com.hoppingmall.mall.user.dto.request.admin.SellerApprovalRequest

interface AdminCommandService {
    fun updateSellerApprovalStatus(sellerId: Long, request: SellerApprovalRequest)
}
