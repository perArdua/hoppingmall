package com.hoppingmall.mall.user.service.admin.strategy

import com.hoppingmall.mall.user.domain.Seller
import com.hoppingmall.mall.user.exception.seller.SellerInvalidApprovalCommandException
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

    fun getCommand(status: Seller.ApprovalStatus): SellerApprovalCommand {
        return commandMap[status] ?: throw SellerInvalidApprovalCommandException()
    }
}
