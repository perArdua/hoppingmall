package com.hoppingmall.mall.membership.enum

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.math.BigDecimal

@DisplayName("MembershipGrade")
@DisplayNameGeneration(ReplaceUnderscores::class)
class MembershipGradeTest {

    @Nested
    @DisplayName("fromTotalSpent")
    inner class FromTotalSpent {
        @Test
        fun 누적_금액_0원이면_BRONZE() {
            val grade = MembershipGrade.fromTotalSpent(BigDecimal.ZERO)
            assertEquals(MembershipGrade.BRONZE, grade)
        }

        @Test
        fun 누적_금액_99999원이면_BRONZE() {
            val grade = MembershipGrade.fromTotalSpent(BigDecimal("99999"))
            assertEquals(MembershipGrade.BRONZE, grade)
        }

        @Test
        fun 누적_금액_100000원이면_SILVER() {
            val grade = MembershipGrade.fromTotalSpent(BigDecimal("100000"))
            assertEquals(MembershipGrade.SILVER, grade)
        }

        @Test
        fun 누적_금액_499999원이면_SILVER() {
            val grade = MembershipGrade.fromTotalSpent(BigDecimal("499999"))
            assertEquals(MembershipGrade.SILVER, grade)
        }

        @Test
        fun 누적_금액_500000원이면_GOLD() {
            val grade = MembershipGrade.fromTotalSpent(BigDecimal("500000"))
            assertEquals(MembershipGrade.GOLD, grade)
        }

        @Test
        fun 누적_금액_1000000원이면_PLATINUM() {
            val grade = MembershipGrade.fromTotalSpent(BigDecimal("1000000"))
            assertEquals(MembershipGrade.PLATINUM, grade)
        }

        @Test
        fun 누적_금액_5000000원이면_DIAMOND() {
            val grade = MembershipGrade.fromTotalSpent(BigDecimal("5000000"))
            assertEquals(MembershipGrade.DIAMOND, grade)
        }

        @Test
        fun 누적_금액_10000000원이면_DIAMOND() {
            val grade = MembershipGrade.fromTotalSpent(BigDecimal("10000000"))
            assertEquals(MembershipGrade.DIAMOND, grade)
        }
    }

    @Nested
    @DisplayName("nextGrade")
    inner class NextGrade {
        @Test
        fun BRONZE의_다음_등급은_SILVER() {
            assertEquals(MembershipGrade.SILVER, MembershipGrade.BRONZE.nextGrade())
        }

        @Test
        fun SILVER의_다음_등급은_GOLD() {
            assertEquals(MembershipGrade.GOLD, MembershipGrade.SILVER.nextGrade())
        }

        @Test
        fun GOLD의_다음_등급은_PLATINUM() {
            assertEquals(MembershipGrade.PLATINUM, MembershipGrade.GOLD.nextGrade())
        }

        @Test
        fun PLATINUM의_다음_등급은_DIAMOND() {
            assertEquals(MembershipGrade.DIAMOND, MembershipGrade.PLATINUM.nextGrade())
        }

        @Test
        fun DIAMOND의_다음_등급은_없음() {
            assertNull(MembershipGrade.DIAMOND.nextGrade())
        }
    }

    @Nested
    @DisplayName("등급별 혜택")
    inner class GradeBenefits {
        @Test
        fun BRONZE_혜택_검증() {
            val grade = MembershipGrade.BRONZE
            assertEquals("브론즈", grade.gradeName)
            assertEquals(BigDecimal("0.01"), grade.pointEarningRate)
            assertEquals(BigDecimal.ZERO, grade.discountRate)
        }

        @Test
        fun SILVER_혜택_검증() {
            val grade = MembershipGrade.SILVER
            assertEquals("실버", grade.gradeName)
            assertEquals(BigDecimal("0.02"), grade.pointEarningRate)
            assertEquals(BigDecimal("0.01"), grade.discountRate)
        }

        @Test
        fun DIAMOND_혜택_검증() {
            val grade = MembershipGrade.DIAMOND
            assertEquals("다이아몬드", grade.gradeName)
            assertEquals(BigDecimal("0.07"), grade.pointEarningRate)
            assertEquals(BigDecimal("0.05"), grade.discountRate)
        }
    }
}
