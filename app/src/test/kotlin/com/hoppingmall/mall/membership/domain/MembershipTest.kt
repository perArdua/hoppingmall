package com.hoppingmall.mall.membership.domain

import com.hoppingmall.mall.membership.enum.MembershipGrade
import com.hoppingmall.mall.support.fixture.fixture
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.math.BigDecimal

@DisplayName("Membership")
@DisplayNameGeneration(ReplaceUnderscores::class)
class MembershipTest {

    @Nested
    @DisplayName("create")
    inner class Create {
        @Test
        fun 멤버십_생성_성공() {
            val membership = Membership.create(userId = 1L)

            assertEquals(1L, membership.userId)
            assertEquals(MembershipGrade.BRONZE, membership.grade)
            assertEquals(BigDecimal.ZERO, membership.totalSpent)
        }
    }

    @Nested
    @DisplayName("addPurchaseAmount")
    inner class AddPurchaseAmount {
        @Test
        fun 구매_금액_추가_성공() {
            val membership = Membership.Companion.fixture()

            membership.addPurchaseAmount(BigDecimal("50000"))

            assertEquals(BigDecimal("50000"), membership.totalSpent)
        }

        @Test
        fun 구매_금액_누적_성공() {
            val membership = Membership.Companion.fixture(totalSpent = BigDecimal("50000"))

            membership.addPurchaseAmount(BigDecimal("30000"))

            assertEquals(BigDecimal("80000"), membership.totalSpent)
        }

        @Test
        fun 음수_금액_추가_시_예외_발생() {
            val membership = Membership.Companion.fixture()

            assertThrows(IllegalArgumentException::class.java) {
                membership.addPurchaseAmount(BigDecimal("-1000"))
            }
        }

        @Test
        fun 영_금액_추가_시_예외_발생() {
            val membership = Membership.Companion.fixture()

            assertThrows(IllegalArgumentException::class.java) {
                membership.addPurchaseAmount(BigDecimal.ZERO)
            }
        }

        @Test
        fun 구매_금액_추가_시_자동_등급_승급() {
            val membership = Membership.Companion.fixture()

            membership.addPurchaseAmount(BigDecimal("100000"))

            assertEquals(MembershipGrade.SILVER, membership.grade)
            assertEquals(BigDecimal("100000"), membership.totalSpent)
        }

        @Test
        fun BRONZE에서_GOLD로_한번에_승급() {
            val membership = Membership.Companion.fixture()

            membership.addPurchaseAmount(BigDecimal("500000"))

            assertEquals(MembershipGrade.GOLD, membership.grade)
        }
    }

    @Nested
    @DisplayName("upgradeGradeIfEligible")
    inner class UpgradeGradeIfEligible {
        @Test
        fun 등급_조건_미충족_시_유지() {
            val membership = Membership.Companion.fixture(totalSpent = BigDecimal("50000"))

            membership.upgradeGradeIfEligible()

            assertEquals(MembershipGrade.BRONZE, membership.grade)
        }

        @Test
        fun 등급_조건_충족_시_승급() {
            val membership = Membership.Companion.fixture(totalSpent = BigDecimal("100000"))

            membership.upgradeGradeIfEligible()

            assertEquals(MembershipGrade.SILVER, membership.grade)
        }

        @Test
        fun 이미_높은_등급이면_강등되지_않음() {
            val membership = Membership.Companion.fixture(
                grade = MembershipGrade.GOLD,
                totalSpent = BigDecimal("100000")
            )

            membership.upgradeGradeIfEligible()

            assertEquals(MembershipGrade.GOLD, membership.grade)
        }
    }
}
