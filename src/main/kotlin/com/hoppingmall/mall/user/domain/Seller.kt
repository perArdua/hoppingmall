package com.hoppingmall.mall.user.domain

import com.hoppingmall.mall.global.common.entity.BaseEntity
import jakarta.persistence.*
import org.hibernate.annotations.Filter

@Filter(name = "softDeleteFilter", condition = "deleted_at IS NULL")
@Entity
@Table(name = "sellers")
class Seller private constructor(
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

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
            return Seller(user, businessNumber, ApprovalStatus.PENDING)
        }
    }

    enum class ApprovalStatus {
        PENDING, APPROVED, REJECTED
    }
}
