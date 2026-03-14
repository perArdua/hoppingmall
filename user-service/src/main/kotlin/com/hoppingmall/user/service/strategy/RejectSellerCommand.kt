package com.hoppingmall.user.service.strategy

import com.hoppingmall.user.domain.Seller
import org.springframework.stereotype.Component

@Component
class RejectSellerCommand : SellerApprovalCommand {
    override fun execute(seller: Seller) {
        seller.reject()
    }
}
