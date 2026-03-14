package com.hoppingmall.user.service

import com.hoppingmall.user.dto.response.MembershipResponse
import java.math.BigDecimal

interface MembershipCommandService {
    fun createMembership(userId: Long): MembershipResponse
    fun addPurchaseAmount(userId: Long, amount: BigDecimal): MembershipResponse
}
