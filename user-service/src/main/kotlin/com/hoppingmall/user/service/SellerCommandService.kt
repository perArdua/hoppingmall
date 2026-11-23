package com.hoppingmall.user.service

import com.hoppingmall.user.dto.request.SellerApplyRequest
import com.hoppingmall.user.dto.response.SellerApplicationResponse

interface SellerCommandService {
    fun apply(userId: Long, request: SellerApplyRequest): SellerApplicationResponse
}
