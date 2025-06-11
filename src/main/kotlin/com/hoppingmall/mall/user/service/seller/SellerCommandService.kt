package com.hoppingmall.mall.user.service.seller

import com.hoppingmall.mall.user.dto.request.seller.SellerApplyRequest

interface SellerCommandService {
    fun apply(userId: Long, request: SellerApplyRequest)
}
