package com.hoppingmall.mall.user.dto.request.admin

import com.hoppingmall.mall.user.domain.Seller

data class SellerApprovalRequest(
    val approvalStatus: String
) {
    fun toApprovalStatus(): Seller.ApprovalStatus =
        runCatching { Seller.ApprovalStatus.valueOf(approvalStatus.uppercase()) }
            .getOrElse { throw IllegalArgumentException("허용되지 않은 승인 상태입니다: $approvalStatus") }
}