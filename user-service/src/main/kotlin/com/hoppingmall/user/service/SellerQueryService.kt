package com.hoppingmall.user.service

import com.hoppingmall.user.domain.Seller
import com.hoppingmall.user.dto.response.SellerApplicationPageResponse
import com.hoppingmall.user.dto.response.SellerApplicationResponse

interface SellerQueryService {
    fun getMyApplication(userId: Long): SellerApplicationResponse

    fun getApplications(status: Seller.ApprovalStatus?, page: Int, size: Int, keyword: String? = null): SellerApplicationPageResponse
}
