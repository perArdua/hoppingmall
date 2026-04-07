package com.hoppingmall.user.domain

import com.hoppingmall.user.domain.enums.MembershipGrade
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import kotlin.test.assertEquals

@DisplayName("Membership 단위 테스트")
@DisplayNameGeneration(ReplaceUnderscores::class)
class MembershipTest {

    @Test
    fun create는_기본_BRONZE_멤버십을_생성한다() {
        val membership = Membership.create(1L)

        assertEquals(1L, membership.userId)
        assertEquals(MembershipGrade.BRONZE, membership.grade)
        assertEquals(BigDecimal.ZERO, membership.totalSpent)
    }

    @Test
    fun addPurchaseAmount는_양수_금액을_누적한다() {
        val membership = Membership.create(2L)

        membership.addPurchaseAmount(BigDecimal("50000"))

        assertEquals(BigDecimal("50000"), membership.totalSpent)
        assertEquals(MembershipGrade.BRONZE, membership.grade)
    }

    @Test
    fun addPurchaseAmount는_0_이하_금액이면_예외가_발생한다() {
        val membership = Membership.create(3L)

        assertThrows<IllegalArgumentException> {
            membership.addPurchaseAmount(BigDecimal.ZERO)
        }
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

            assertEquals(expectedGrade, membership.grade)
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

        assertEquals(MembershipGrade.GOLD, membership.grade)
    }
}
