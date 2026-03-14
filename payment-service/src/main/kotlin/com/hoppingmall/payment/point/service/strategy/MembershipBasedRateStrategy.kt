package com.hoppingmall.payment.point.service.strategy

import com.hoppingmall.payment.port.MembershipQueryPort
import org.springframework.stereotype.Component
import java.math.BigDecimal

@Component
class MembershipBasedRateStrategy(
    private val membershipQueryPort: MembershipQueryPort
) : PointEarnRateStrategy {

    override fun getEarnRate(userId: Long): BigDecimal {
        return membershipQueryPort.getPointEarningRate(userId)
    }
}
