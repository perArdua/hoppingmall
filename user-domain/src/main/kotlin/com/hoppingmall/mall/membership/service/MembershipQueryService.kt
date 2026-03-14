package com.hoppingmall.mall.membership.service

import com.hoppingmall.mall.membership.dto.response.MembershipResponse

interface MembershipQueryService {
    fun getMembershipByUserId(userId: Long): MembershipResponse
}
