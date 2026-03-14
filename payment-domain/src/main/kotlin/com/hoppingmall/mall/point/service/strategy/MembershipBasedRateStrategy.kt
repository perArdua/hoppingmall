package com.hoppingmall.mall.point.service.strategy

import com.hoppingmall.mall.user.api.MembershipQueryPort
import org.springframework.stereotype.Component
import java.math.BigDecimal

@Component
class MembershipBasedRateStrategy(
    private val membershipQueryPort: MembershipQueryPort
) : PointEarnRateStrategy {

    override fun getEarnRate(userId: Long): BigDecimal {
        return membershipQueryPort.getPointEarnRate(userId)
    }
}
