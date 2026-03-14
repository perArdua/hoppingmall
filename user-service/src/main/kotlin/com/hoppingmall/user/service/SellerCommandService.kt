package com.hoppingmall.user.service

import com.hoppingmall.user.dto.request.SellerApplyRequest

interface SellerCommandService {
    fun apply(userId: Long, request: SellerApplyRequest)
}
