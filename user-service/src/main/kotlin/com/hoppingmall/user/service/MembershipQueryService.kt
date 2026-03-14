package com.hoppingmall.user.service

import com.hoppingmall.user.dto.response.MembershipResponse

interface MembershipQueryService {
    fun getMembershipByUserId(userId: Long): MembershipResponse
}
