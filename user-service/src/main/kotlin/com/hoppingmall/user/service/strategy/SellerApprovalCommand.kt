package com.hoppingmall.user.service.strategy

import com.hoppingmall.user.domain.Seller

fun interface SellerApprovalCommand {
    fun execute(seller: Seller)
}
