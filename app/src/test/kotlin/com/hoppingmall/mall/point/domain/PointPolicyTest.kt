package com.hoppingmall.mall.point.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.math.BigDecimal

@DisplayName("PointPolicy")
@DisplayNameGeneration(ReplaceUnderscores::class)
class PointPolicyTest {

    @Nested
    @DisplayName("포인트_정책_생성")
    inner class Create {
        @Test
        fun `포인트_정책_생성_성공`() {
            // given
            val policyName = "테스트 정책"
            val earnRate = BigDecimal("0.01")
            val maxEarnRate = BigDecimal("0.02")
            val minUseAmount = BigDecimal("1000")
            val maxUseAmount = BigDecimal("10000")
            val description = "테스트용 정책"

            // when
            val policy = PointPolicy.create(
                policyName = policyName,
                earnRate = earnRate,
                maxEarnRate = maxEarnRate,
                minUseAmount = minUseAmount,
                maxUseAmount = maxUseAmount,
                description = description
            )

            // then
            assertEquals(policyName, policy.policyName)
            assertEquals(earnRate, policy.earnRate)
            assertEquals(maxEarnRate, policy.maxEarnRate)
            assertEquals(minUseAmount, policy.minUseAmount)
            assertEquals(maxUseAmount, policy.maxUseAmount)
            assertFalse(policy.isActive) // 기본적으로 비활성 상태
            assertEquals(description, policy.description)
        }

        @Test
        fun `적립률_0이하_예외발생`() {
            // when & then
            assertThrows(IllegalArgumentException::class.java) {
                PointPolicy.create(
                    policyName = "테스트 정책",
                    earnRate = BigDecimal.ZERO,
                    maxEarnRate = BigDecimal("0.02"),
                    minUseAmount = BigDecimal("1000"),
                    maxUseAmount = BigDecimal("10000")
                )
            }
        }

        @Test
        fun `최대적립률_0이하_예외발생`() {
            // when & then
            assertThrows(IllegalArgumentException::class.java) {
                PointPolicy.create(
                    policyName = "테스트 정책",
                    earnRate = BigDecimal("0.01"),
                    maxEarnRate = BigDecimal.ZERO,
                    minUseAmount = BigDecimal("1000"),
                    maxUseAmount = BigDecimal("10000")
                )
            }
        }

        @Test
        fun `적립률_최대적립률초과_예외발생`() {
            // when & then
            assertThrows(IllegalArgumentException::class.java) {
                PointPolicy.create(
                    policyName = "테스트 정책",
                    earnRate = BigDecimal("0.03"),
                    maxEarnRate = BigDecimal("0.02"),
                    minUseAmount = BigDecimal("1000"),
                    maxUseAmount = BigDecimal("10000")
                )
            }
        }

        @Test
        fun `최소사용금액_음수_예외발생`() {
            // when & then
            assertThrows(IllegalArgumentException::class.java) {
                PointPolicy.create(
                    policyName = "테스트 정책",
                    earnRate = BigDecimal("0.01"),
                    maxEarnRate = BigDecimal("0.02"),
                    minUseAmount = BigDecimal("-1000"),
                    maxUseAmount = BigDecimal("10000")
                )
            }
        }

        @Test
        fun `최대사용금액_0이하_예외발생`() {
            // when & then
            assertThrows(IllegalArgumentException::class.java) {
                PointPolicy.create(
                    policyName = "테스트 정책",
                    earnRate = BigDecimal("0.01"),
                    maxEarnRate = BigDecimal("0.02"),
                    minUseAmount = BigDecimal("1000"),
                    maxUseAmount = BigDecimal.ZERO
                )
            }
        }

        @Test
        fun `최소사용금액_최대사용금액초과_예외발생`() {
            // when & then
            assertThrows(IllegalArgumentException::class.java) {
                PointPolicy.create(
                    policyName = "테스트 정책",
                    earnRate = BigDecimal("0.01"),
                    maxEarnRate = BigDecimal("0.02"),
                    minUseAmount = BigDecimal("20000"),
                    maxUseAmount = BigDecimal("10000")
                )
            }
        }
    }

    @Nested
    @DisplayName("포인트_정책_활성화")
    inner class Activate {
        @Test
        fun `포인트_정책_활성화_성공`() {
            // given
            val policy = PointPolicy.create(
                policyName = "테스트 정책",
                earnRate = BigDecimal("0.01"),
                maxEarnRate = BigDecimal("0.02"),
                minUseAmount = BigDecimal("1000"),
                maxUseAmount = BigDecimal("10000")
            )

            // when
            val activatedPolicy = policy.activate()

            // then
            assertTrue(activatedPolicy.isActive)
            assertEquals(policy.policyName, activatedPolicy.policyName)
            assertEquals(policy.earnRate, activatedPolicy.earnRate)
        }
    }

    @Nested
    @DisplayName("포인트_정책_비활성화")
    inner class Deactivate {
        @Test
        fun `포인트_정책_비활성화_성공`() {
            // given
            val policy = PointPolicy.create(
                policyName = "테스트 정책",
                earnRate = BigDecimal("0.01"),
                maxEarnRate = BigDecimal("0.02"),
                minUseAmount = BigDecimal("1000"),
                maxUseAmount = BigDecimal("10000")
            ).activate()

            // when
            val deactivatedPolicy = policy.deactivate()

            // then
            assertFalse(deactivatedPolicy.isActive)
            assertEquals(policy.policyName, deactivatedPolicy.policyName)
            assertEquals(policy.earnRate, deactivatedPolicy.earnRate)
        }
    }

    @Nested
    @DisplayName("포인트_정책_수정")
    inner class Update {
        @Test
        fun `포인트_정책_수정_성공`() {
            // given
            val policy = PointPolicy.create(
                policyName = "기존 정책",
                earnRate = BigDecimal("0.01"),
                maxEarnRate = BigDecimal("0.02"),
                minUseAmount = BigDecimal("1000"),
                maxUseAmount = BigDecimal("10000"),
                description = "기존 설명"
            )

            val newPolicyName = "수정된 정책"
            val newEarnRate = BigDecimal("0.02")
            val newMaxEarnRate = BigDecimal("0.03")
            val newMinUseAmount = BigDecimal("2000")
            val newMaxUseAmount = BigDecimal("20000")
            val newDescription = "수정된 설명"

            // when
            val updatedPolicy = policy.update(
                policyName = newPolicyName,
                earnRate = newEarnRate,
                maxEarnRate = newMaxEarnRate,
                minUseAmount = newMinUseAmount,
                maxUseAmount = newMaxUseAmount,
                description = newDescription
            )

            // then
            assertEquals(newPolicyName, updatedPolicy.policyName)
            assertEquals(newEarnRate, updatedPolicy.earnRate)
            assertEquals(newMaxEarnRate, updatedPolicy.maxEarnRate)
            assertEquals(newMinUseAmount, updatedPolicy.minUseAmount)
            assertEquals(newMaxUseAmount, updatedPolicy.maxUseAmount)
            assertEquals(newDescription, updatedPolicy.description)
            assertEquals(policy.isActive, updatedPolicy.isActive) // 활성 상태는 변경되지 않음
        }
    }

    @Nested
    @DisplayName("포인트_적립_계산")
    inner class CalculateEarnPoints {
        @Test
        fun `포인트_적립_계산_성공`() {
            // given
            val policy = PointPolicy.create(
                policyName = "테스트 정책",
                earnRate = BigDecimal("0.01"), // 1%
                maxEarnRate = BigDecimal("0.02"),
                minUseAmount = BigDecimal("1000"),
                maxUseAmount = BigDecimal("10000")
            )
            val purchaseAmount = BigDecimal("50000")

            // when
            val earnPoints = policy.calculateEarnPoints(purchaseAmount)

            // then
            assertEquals(BigDecimal("500.00"), earnPoints) // 50000 * 0.01 = 500.00
        }

        @Test
        fun `구매금액_0이하_예외발생`() {
            // given
            val policy = PointPolicy.create(
                policyName = "테스트 정책",
                earnRate = BigDecimal("0.01"),
                maxEarnRate = BigDecimal("0.02"),
                minUseAmount = BigDecimal("1000"),
                maxUseAmount = BigDecimal("10000")
            )

            // when & then
            assertThrows(IllegalArgumentException::class.java) {
                policy.calculateEarnPoints(BigDecimal.ZERO)
            }
        }

        @Test
        fun `구매금액_음수_예외발생`() {
            // given
            val policy = PointPolicy.create(
                policyName = "테스트 정책",
                earnRate = BigDecimal("0.01"),
                maxEarnRate = BigDecimal("0.02"),
                minUseAmount = BigDecimal("1000"),
                maxUseAmount = BigDecimal("10000")
            )

            // when & then
            assertThrows(IllegalArgumentException::class.java) {
                policy.calculateEarnPoints(BigDecimal("-1000"))
            }
        }
    }

    @Nested
    @DisplayName("포인트_사용_가능성_확인")
    inner class CanUsePoints {
        @Test
        fun `포인트_사용_가능한_금액범위`() {
            // given
            val policy = PointPolicy.create(
                policyName = "테스트 정책",
                earnRate = BigDecimal("0.01"),
                maxEarnRate = BigDecimal("0.02"),
                minUseAmount = BigDecimal("1000"),
                maxUseAmount = BigDecimal("10000")
            )

            // when & then
            assertTrue(policy.canUsePoints(BigDecimal("5000"))) // 범위 내
            assertTrue(policy.canUsePoints(BigDecimal("1000"))) // 최소값
            assertTrue(policy.canUsePoints(BigDecimal("10000"))) // 최대값
        }

        @Test
        fun `포인트_사용_불가능한_금액범위`() {
            // given
            val policy = PointPolicy.create(
                policyName = "테스트 정책",
                earnRate = BigDecimal("0.01"),
                maxEarnRate = BigDecimal("0.02"),
                minUseAmount = BigDecimal("1000"),
                maxUseAmount = BigDecimal("10000")
            )

            // when & then
            assertFalse(policy.canUsePoints(BigDecimal("500"))) // 최소값 미만
            assertFalse(policy.canUsePoints(BigDecimal("15000"))) // 최대값 초과
        }

        @Test
        fun `포인트_사용_경계값_테스트`() {
            // given
            val policy = PointPolicy.create(
                policyName = "테스트 정책",
                earnRate = BigDecimal("0.01"),
                maxEarnRate = BigDecimal("0.02"),
                minUseAmount = BigDecimal("1000"),
                maxUseAmount = BigDecimal("10000")
            )

            // when & then
            assertTrue(policy.canUsePoints(BigDecimal("1000"))) // 최소값 (포함)
            assertTrue(policy.canUsePoints(BigDecimal("10000"))) // 최대값 (포함)
            assertFalse(policy.canUsePoints(BigDecimal("999"))) // 최소값 미만
            assertFalse(policy.canUsePoints(BigDecimal("10001"))) // 최대값 초과
        }
    }
} 