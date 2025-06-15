package com.hoppingmall.mall.user.service.admin.strategy

import com.hoppingmall.mall.user.domain.Seller

fun interface SellerApprovalCommand {
    fun execute(seller: Seller)
}
