package com.hoppingmall.mall.user.service.admin.strategy

import com.hoppingmall.mall.user.domain.Seller
import org.springframework.stereotype.Component

@Component
class ApproveSellerCommand : SellerApprovalCommand {
    override fun execute(seller: Seller) {
        seller.approve()
    }
}
