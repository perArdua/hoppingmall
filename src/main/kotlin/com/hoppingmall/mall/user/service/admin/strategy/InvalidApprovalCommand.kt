package com.hoppingmall.mall.user.service.admin.strategy

import com.hoppingmall.mall.user.domain.Seller
import com.hoppingmall.mall.user.exception.seller.SellerInvalidApprovalStatusException
import org.springframework.stereotype.Component

@Component
class InvalidApprovalCommand : SellerApprovalCommand {
    override fun execute(seller: Seller) {
        throw SellerInvalidApprovalStatusException()
    }
}
