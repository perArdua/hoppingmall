package com.hoppingmall.user.domain

import com.hoppingmall.common.BaseEntity
import jakarta.persistence.*
import org.hibernate.annotations.Filter

@Filter(name = "softDeleteFilter", condition = "deleted_at IS NULL")
@Entity
@Table(name = "sellers")
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
        this.approvalStatus = ApprovalStatus.APPROVED
    }

    fun reject() {
        this.approvalStatus = ApprovalStatus.REJECTED
    }

    fun getApprovalStatus(): ApprovalStatus = this.approvalStatus

    companion object {
        fun create(user: User, businessNumber: String): Seller {
            return Seller(user.id!!, businessNumber, ApprovalStatus.PENDING)
        }
    }

    enum class ApprovalStatus {
        PENDING, APPROVED, REJECTED
    }
}
