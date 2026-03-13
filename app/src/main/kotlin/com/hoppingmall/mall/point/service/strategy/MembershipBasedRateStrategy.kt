package com.hoppingmall.mall.point.service.strategy

import com.hoppingmall.mall.membership.domain.repository.MembershipRepository
import com.hoppingmall.mall.membership.enum.MembershipGrade
import org.springframework.stereotype.Component
import java.math.BigDecimal

@Component
class MembershipBasedRateStrategy(
    private val membershipRepository: MembershipRepository
) : PointEarnRateStrategy {

    override fun getEarnRate(userId: Long): BigDecimal {
        val membership = membershipRepository.findByUserId(userId)
        return membership?.grade?.pointEarningRate ?: MembershipGrade.BRONZE.pointEarningRate
    }
}
