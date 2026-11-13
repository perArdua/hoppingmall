package com.hoppingmall.user.domain

import com.hoppingmall.user.domain.enums.MembershipGrade
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import java.math.BigDecimal

@DisplayName("Membership 구매액 되돌리기")
@DisplayNameGeneration(ReplaceUnderscores::class)
class MembershipRevertTest {

    @Test
    fun 되돌리면_누적액이_줄고_등급이_깎인_누적액으로_다시_정해진다() {
        val membership = Membership.create(1L)
        membership.addPurchaseAmount(BigDecimal("600000"))

        membership.revertPurchaseAmount(BigDecimal("150000"))

        assertThat(membership.totalSpent).isEqualByComparingTo("450000")
        assertThat(membership.grade).isEqualTo(MembershipGrade.SILVER)
    }

    @Test
    fun 누적액보다_크게_되돌려도_0_아래로_내려가지_않는다() {
        val membership = Membership.create(2L)
        membership.addPurchaseAmount(BigDecimal("120000"))

        membership.revertPurchaseAmount(BigDecimal("500000"))

        assertThat(membership.totalSpent).isEqualByComparingTo("0")
        assertThat(membership.grade).isEqualTo(MembershipGrade.BRONZE)
    }

    @Test
    fun 되돌릴_금액이_0_이하이면_예외가_발생한다() {
        val membership = Membership.create(3L)

        assertThatThrownBy { membership.revertPurchaseAmount(BigDecimal.ZERO) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }
}
