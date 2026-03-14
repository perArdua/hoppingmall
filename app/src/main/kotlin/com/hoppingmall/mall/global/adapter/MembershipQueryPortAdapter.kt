package com.hoppingmall.mall.global.adapter

import com.hoppingmall.mall.membership.domain.repository.MembershipRepository
import com.hoppingmall.mall.membership.enum.MembershipGrade
import com.hoppingmall.mall.user.api.MembershipQueryPort
import org.springframework.stereotype.Component
import java.math.BigDecimal

@Component
class MembershipQueryPortAdapter(
    private val membershipRepository: MembershipRepository
) : MembershipQueryPort {

    override fun getPointEarnRate(userId: Long): BigDecimal {
        val membership = membershipRepository.findByUserId(userId)
        return membership?.grade?.pointEarningRate ?: MembershipGrade.BRONZE.pointEarningRate
    }
}
