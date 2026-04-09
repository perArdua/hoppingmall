package com.hoppingmall.payment.point.domain

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import java.math.BigDecimal

@DisplayName("PointPolicy")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class PointPolicyTest {

    private fun createPolicy(
        earnRate: BigDecimal = BigDecimal("0.01"),
        maxEarnRate: BigDecimal = BigDecimal("0.05"),
        minUseAmount: BigDecimal = BigDecimal("100"),
        maxUseAmount: BigDecimal = BigDecimal("10000")
    ): PointPolicy {
        return PointPolicy.create(
            policyName = "기본 정책",
            earnRate = earnRate,
            maxEarnRate = maxEarnRate,
            minUseAmount = minUseAmount,
            maxUseAmount = maxUseAmount
        )
    }

    @Test
    fun 정책_생성_시_비활성_상태이다() {
        val policy = createPolicy()

        assertThat(policy.isActive).isFalse()
        assertThat(policy.policyName).isEqualTo("기본 정책")
    }

    @Test
    fun 정책을_활성화할_수_있다() {
        val policy = createPolicy()

        val activated = policy.activate()

        assertThat(activated.isActive).isTrue()
    }

    @Test
    fun 정책을_비활성화할_수_있다() {
        val policy = createPolicy().activate()

        val deactivated = policy.deactivate()

        assertThat(deactivated.isActive).isFalse()
    }

    @Test
    fun 구매_금액에_따라_적립_포인트를_계산한다() {
        val policy = createPolicy(earnRate = BigDecimal("0.05"))

        val earned = policy.calculateEarnPoints(BigDecimal("10000"))

        assertThat(earned).isEqualByComparingTo(BigDecimal("500"))
    }

    @Test
    fun 사용_금액이_범위_내이면_사용_가능하다() {
        val policy = createPolicy(minUseAmount = BigDecimal("100"), maxUseAmount = BigDecimal("10000"))

        assertThat(policy.canUsePoints(BigDecimal("500"))).isTrue()
    }

    @Test
    fun 사용_금액이_최소_미만이면_사용_불가하다() {
        val policy = createPolicy(minUseAmount = BigDecimal("100"))

        assertThat(policy.canUsePoints(BigDecimal("50"))).isFalse()
    }

    @Test
    fun 사용_금액이_최대_초과이면_사용_불가하다() {
        val policy = createPolicy(maxUseAmount = BigDecimal("10000"))

        assertThat(policy.canUsePoints(BigDecimal("20000"))).isFalse()
    }

    @Test
    fun 적립률이_0_이하이면_예외가_발생한다() {
        assertThatThrownBy { createPolicy(earnRate = BigDecimal.ZERO) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun 적립률이_최대_적립률을_초과하면_예외가_발생한다() {
        assertThatThrownBy { createPolicy(earnRate = BigDecimal("0.10"), maxEarnRate = BigDecimal("0.05")) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun 정책을_업데이트할_수_있다() {
        val policy = createPolicy()

        val updated = policy.update(
            policyName = "수정된 정책",
            earnRate = BigDecimal("0.02"),
            maxEarnRate = BigDecimal("0.10"),
            minUseAmount = BigDecimal("200"),
            maxUseAmount = BigDecimal("50000"),
            description = "설명"
        )

        assertThat(updated.policyName).isEqualTo("수정된 정책")
        assertThat(updated.earnRate).isEqualByComparingTo(BigDecimal("0.02"))
    }
}
