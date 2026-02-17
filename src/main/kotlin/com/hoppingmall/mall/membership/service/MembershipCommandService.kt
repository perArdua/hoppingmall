package com.hoppingmall.mall.membership.service

import com.hoppingmall.mall.membership.dto.response.MembershipResponse
import java.math.BigDecimal

interface MembershipCommandService {
    fun createMembership(userId: Long): MembershipResponse
    fun addPurchaseAmount(userId: Long, amount: BigDecimal): MembershipResponse
}
