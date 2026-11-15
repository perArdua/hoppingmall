package com.hoppingmall.user.dto.response

import com.hoppingmall.user.domain.Seller
import java.time.LocalDateTime

data class SellerApplicationResponse(
    val id: Long,
    val userId: Long,
    val businessNumber: String,
    val status: Seller.ApprovalStatus,
    val rejectionReason: String?,
    val appliedAt: LocalDateTime,
    val updatedAt: LocalDateTime?
) {
    companion object {
        fun from(seller: Seller): SellerApplicationResponse = SellerApplicationResponse(
            id = seller.id!!,
            userId = seller.userId,
            businessNumber = seller.businessNumber,
            status = seller.getApprovalStatus(),
            rejectionReason = seller.rejectionReason,
            appliedAt = seller.createdAt,
            updatedAt = seller.updatedAt
        )
    }
}
