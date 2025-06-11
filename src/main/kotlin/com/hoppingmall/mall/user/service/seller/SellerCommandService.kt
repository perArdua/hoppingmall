package com.hoppingmall.mall.user.service.seller

import com.hoppingmall.mall.user.dto.request.SellerApplyRequest

interface SellerCommandService {
    fun apply(userId: Long, request: SellerApplyRequest)
}
