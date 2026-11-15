package com.hoppingmall.user.dto.response

import com.hoppingmall.user.domain.Seller
import com.hoppingmall.user.domain.User
import java.time.LocalDateTime

data class AdminSellerApplicationResponse(
    val id: Long,
    val userId: Long,
    val userEmail: String?,
    val userName: String?,
    val businessNumber: String,
    val status: Seller.ApprovalStatus,
    val rejectionReason: String?,
    val appliedAt: LocalDateTime,
    val updatedAt: LocalDateTime?
) {
    companion object {
        fun of(seller: Seller, applicant: User?): AdminSellerApplicationResponse = AdminSellerApplicationResponse(
            id = seller.id!!,
            userId = seller.userId,
            userEmail = applicant?.email?.value,
            userName = applicant?.getName(),
            businessNumber = seller.businessNumber,
            status = seller.getApprovalStatus(),
            rejectionReason = seller.rejectionReason,
            appliedAt = seller.createdAt,
            updatedAt = seller.updatedAt
        )
    }
}
