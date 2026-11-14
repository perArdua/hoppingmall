package com.hoppingmall.user.domain

import com.hoppingmall.common.BaseEntity
import com.hoppingmall.common.EnumNameConverter
import com.hoppingmall.user.exception.seller.SellerAlreadyAppliedException
import com.hoppingmall.user.exception.seller.SellerInvalidApprovalStatusException
import jakarta.persistence.*

@Entity
@Table(
    name = "sellers",
    uniqueConstraints = [UniqueConstraint(name = Seller.USER_ID_UNIQUE_CONSTRAINT, columnNames = ["user_id"])]
)
class Seller private constructor(
    @Column(name = "user_id", nullable = false)
    val userId: Long,

    businessNumber: String,

    @Convert(converter = ApprovalStatusConverter::class)
    @Column(name = "approval_status", nullable = false, length = 32)
    private var approvalStatus: ApprovalStatus = ApprovalStatus.PENDING

) : BaseEntity() {

    @Column(name = "business_number", unique = true, nullable = false)
    var businessNumber: String = businessNumber
        private set

    fun approve() {
        validatePendingStatus()
        this.approvalStatus = ApprovalStatus.APPROVED
    }

    fun reject() {
        validatePendingStatus()
        this.approvalStatus = ApprovalStatus.REJECTED
    }

    fun reapply(businessNumber: String) {
        if (this.approvalStatus != ApprovalStatus.REJECTED) {
            throw SellerAlreadyAppliedException()
        }
        this.businessNumber = businessNumber
        this.approvalStatus = ApprovalStatus.PENDING
    }

    private fun validatePendingStatus() {
        if (this.approvalStatus != ApprovalStatus.PENDING) {
            throw SellerInvalidApprovalStatusException()
        }
    }

    fun getApprovalStatus(): ApprovalStatus = approvalStatus

    companion object {
        const val USER_ID_UNIQUE_CONSTRAINT = "uk_sellers_user_id"

        fun create(user: User, businessNumber: String): Seller =
            Seller(user.id!!, businessNumber, ApprovalStatus.PENDING)
    }

    enum class ApprovalStatus {
        PENDING, APPROVED, REJECTED
    }
}

class ApprovalStatusConverter : EnumNameConverter<Seller.ApprovalStatus>(Seller.ApprovalStatus::class.java)
