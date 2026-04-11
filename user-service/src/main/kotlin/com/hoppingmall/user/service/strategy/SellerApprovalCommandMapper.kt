package com.hoppingmall.user.service.strategy

import com.hoppingmall.user.domain.Seller
import com.hoppingmall.user.exception.seller.SellerInvalidApprovalCommandException
import org.springframework.stereotype.Component

@Component
class SellerApprovalCommandMapper(
    approve: ApproveSellerCommand,
    reject: RejectSellerCommand,
) {
    private val commandMap: Map<Seller.ApprovalStatus, SellerApprovalCommand> = mapOf(
        Seller.ApprovalStatus.APPROVED to approve,
        Seller.ApprovalStatus.REJECTED to reject,
    )

    fun getCommand(status: Seller.ApprovalStatus): SellerApprovalCommand =
        commandMap[status] ?: throw SellerInvalidApprovalCommandException()
}
