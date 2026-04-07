package com.hoppingmall.user.domain

import com.hoppingmall.user.domain.enums.MembershipGrade
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import java.math.BigDecimal

@DisplayName("Membership")
@DisplayNameGeneration(ReplaceUnderscores::class)
class MembershipTest {

    @Test
    fun 기본_BRONZE_멤버십을_생성한다() {
        val membership = Membership.create(1L)

        assertThat(membership.userId).isEqualTo(1L)
        assertThat(membership.grade).isEqualTo(MembershipGrade.BRONZE)
        assertThat(membership.totalSpent).isEqualTo(BigDecimal.ZERO)
    }

    @Test
    fun 양수_금액을_누적한다() {
        val membership = Membership.create(2L)

        membership.addPurchaseAmount(BigDecimal("50000"))

        assertThat(membership.totalSpent).isEqualTo(BigDecimal("50000"))
        assertThat(membership.grade).isEqualTo(MembershipGrade.BRONZE)
    }

    @Test
    fun 금액이_0_이하이면_예외가_발생한다() {
        val membership = Membership.create(3L)

        assertThatThrownBy { membership.addPurchaseAmount(BigDecimal.ZERO) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun 누적_금액_경계에_따라_등급이_승급한다() {
        val expectations = listOf(
            BigDecimal("100000") to MembershipGrade.SILVER,
            BigDecimal("500000") to MembershipGrade.GOLD,
            BigDecimal("1000000") to MembershipGrade.PLATINUM,
            BigDecimal("5000000") to MembershipGrade.DIAMOND
        )

        expectations.forEach { (amount, expectedGrade) ->
            val membership = Membership.create(4L)

            membership.addPurchaseAmount(amount)

            assertThat(membership.grade).isEqualTo(expectedGrade)
        }
    }

    @Test
    fun 이미_높은_등급이면_낮은_구간_금액을_추가해도_등급이_유지된다() {
        val membership = Membership(
            userId = 5L,
            grade = MembershipGrade.GOLD,
            totalSpent = BigDecimal("500000")
        )

        membership.addPurchaseAmount(BigDecimal("1"))

        assertThat(membership.grade).isEqualTo(MembershipGrade.GOLD)
    }
}
