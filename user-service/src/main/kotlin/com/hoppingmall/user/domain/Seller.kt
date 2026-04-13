package com.hoppingmall.user.domain

import com.hoppingmall.common.BaseEntity
import com.hoppingmall.user.exception.seller.SellerInvalidApprovalStatusException
import jakarta.persistence.*

@Entity
@Table(
    name = "sellers",
    indexes = [Index(name = "idx_sellers_user_id", columnList = "user_id")]
)
class Seller private constructor(
    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(name = "business_number", unique = true, nullable = false)
    val businessNumber: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "approval_status", nullable = false)
    private var approvalStatus: ApprovalStatus = ApprovalStatus.PENDING

) : BaseEntity() {

    fun approve() {
        validatePendingStatus()
        this.approvalStatus = ApprovalStatus.APPROVED
    }

    fun reject() {
        validatePendingStatus()
        this.approvalStatus = ApprovalStatus.REJECTED
    }

    private fun validatePendingStatus() {
        if (this.approvalStatus != ApprovalStatus.PENDING) {
            throw SellerInvalidApprovalStatusException()
        }
    }

    fun getApprovalStatus(): ApprovalStatus = approvalStatus

    companion object {
        fun create(user: User, businessNumber: String): Seller =
            Seller(user.id!!, businessNumber, ApprovalStatus.PENDING)
    }

    enum class ApprovalStatus {
        PENDING, APPROVED, REJECTED
    }
}
