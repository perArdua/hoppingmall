package com.hoppingmall.user.dto.request

import com.hoppingmall.user.domain.Seller
import com.hoppingmall.user.exception.seller.SellerInvalidApprovalStatusException
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class SellerApprovalRequest(
    @field:NotBlank(message = "승인 상태는 필수입니다")
    @field:Pattern(regexp = "^(APPROVED|REJECTED)$", message = "허용되지 않은 승인 상태입니다")
    val approvalStatus: String,

    @field:Size(max = Seller.REJECTION_REASON_MAX_LENGTH, message = "거절 사유는 500자 이하여야 합니다")
    val rejectionReason: String? = null
) {
    fun toApprovalStatus(): Seller.ApprovalStatus =
        runCatching { Seller.ApprovalStatus.valueOf(approvalStatus.uppercase()) }
            .getOrElse { throw SellerInvalidApprovalStatusException() }

    fun normalizedRejectionReason(): String? = rejectionReason?.trim()?.takeIf { it.isNotEmpty() }
}
