package com.hoppingmall.mall.point.service

import com.hoppingmall.mall.point.domain.PointPolicy
import com.hoppingmall.mall.point.domain.PointPolicyRepository
import com.hoppingmall.mall.point.dto.request.PointPolicyRequest
import com.hoppingmall.mall.point.dto.response.PointPolicyResponse
import com.hoppingmall.mall.point.exception.PointPolicyNotFoundException
import com.hoppingmall.mall.support.fixture.fixture
import com.hoppingmall.mall.support.fixture.activeFixture
import com.hoppingmall.mall.support.fixture.inactiveFixture
import com.hoppingmall.mall.support.withId
import org.junit.jupiter.api.*
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.mockito.kotlin.*
import java.math.BigDecimal
import java.util.Optional

@DisplayName("PointPolicyServiceImpl")
@DisplayNameGeneration(ReplaceUnderscores::class)
class PointPolicyServiceImplTest {

    private val pointPolicyRepository: PointPolicyRepository = mock()
    private val pointPolicyService = PointPolicyServiceImpl(pointPolicyRepository)

    @Nested
    @DisplayName("createPolicy")
    inner class CreatePolicy {
        @Test
        fun `포인트 정책 생성 성공`() {
            // given
            val request = PointPolicyRequest(
                policyName = "테스트 정책",
                earnRate = BigDecimal("0.01"),
                maxEarnRate = BigDecimal("0.02"),
                minUseAmount = BigDecimal("1000"),
                maxUseAmount = BigDecimal("10000"),
                description = "테스트용 정책"
            )
            val savedPolicy = PointPolicy.activeFixture()

            whenever(pointPolicyRepository.existsByPolicyName(request.policyName)).thenReturn(false)
            whenever(pointPolicyRepository.findByIsActiveTrue()).thenReturn(null)
            whenever(pointPolicyRepository.save(any<PointPolicy>())).thenReturn(savedPolicy)

            // when
            val result = pointPolicyService.createPolicy(request)

            // then
            assertEquals("활성 정책", result.policyName)
            assertEquals(BigDecimal("0.01"), result.earnRate)
            assertTrue(result.isActive)
            verify(pointPolicyRepository).save(any())
            verify(pointPolicyRepository).existsByPolicyName(request.policyName)
        }

        @Test
        fun `이미 존재하는 정책 이름으로 생성 시 예외 발생`() {
            // given
            val request = PointPolicyRequest(
                policyName = "기존 정책",
                earnRate = BigDecimal("0.01"),
                maxEarnRate = BigDecimal("0.02"),
                minUseAmount = BigDecimal("1000"),
                maxUseAmount = BigDecimal("10000"),
                description = "테스트용 정책"
            )

            whenever(pointPolicyRepository.existsByPolicyName(request.policyName)).thenReturn(true)

            // when & then
            val exception = assertThrows(IllegalArgumentException::class.java) {
                pointPolicyService.createPolicy(request)
            }
            assertEquals("이미 존재하는 정책 이름입니다", exception.message)
        }

        @Test
        fun `적립률이 최대 적립률을 초과할 때 예외 발생`() {
            // given
            val request = PointPolicyRequest(
                policyName = "테스트 정책",
                earnRate = BigDecimal("0.03"),
                maxEarnRate = BigDecimal("0.02"),
                minUseAmount = BigDecimal("1000"),
                maxUseAmount = BigDecimal("10000"),
                description = "테스트용 정책"
            )

            // when & then
            val exception = assertThrows(IllegalArgumentException::class.java) {
                pointPolicyService.createPolicy(request)
            }
            assertEquals("적립률은 최대 적립률을 초과할 수 없습니다", exception.message)
        }
    }

    @Nested
    @DisplayName("updatePolicy")
    inner class UpdatePolicy {
        @Test
        fun `포인트 정책 수정 성공`() {
            // given
            val policyId = 1L
            val request = PointPolicyRequest(
                policyName = "수정된 정책",
                earnRate = BigDecimal("0.02"),
                maxEarnRate = BigDecimal("0.03"),
                minUseAmount = BigDecimal("2000"),
                maxUseAmount = BigDecimal("20000"),
                description = "수정된 정책"
            )
            val existingPolicy = PointPolicy.inactiveFixture()
            val updatedPolicy = existingPolicy.update(
                policyName = request.policyName,
                earnRate = request.earnRate,
                maxEarnRate = request.maxEarnRate,
                minUseAmount = request.minUseAmount,
                maxUseAmount = request.maxUseAmount,
                description = request.description
            ).withId(1L)

            whenever(pointPolicyRepository.findById(policyId)).thenReturn(Optional.of(existingPolicy))
            whenever(pointPolicyRepository.save(any<PointPolicy>())).thenReturn(updatedPolicy)

            // when
            val result = pointPolicyService.updatePolicy(policyId, request)

            // then
            assertEquals("수정된 정책", result.policyName)
            assertEquals(BigDecimal("0.02"), result.earnRate)
            assertEquals(BigDecimal("0.03"), result.maxEarnRate)
            assertEquals(BigDecimal("2000"), result.minUseAmount)
            assertEquals(BigDecimal("20000"), result.maxUseAmount)
            assertEquals("수정된 정책", result.description)
            verify(pointPolicyRepository).findById(policyId)
            verify(pointPolicyRepository).save(any())
        }

        @Test
        fun `존재하지 않는 정책 수정 시 예외 발생`() {
            // given
            val policyId = 999L
            val request = PointPolicyRequest(
                policyName = "수정된 정책",
                earnRate = BigDecimal("0.02"),
                maxEarnRate = BigDecimal("0.03"),
                minUseAmount = BigDecimal("2000"),
                maxUseAmount = BigDecimal("20000"),
                description = "수정된 정책"
            )

            whenever(pointPolicyRepository.findById(policyId)).thenReturn(Optional.empty())

            // when & then
            assertThrows(PointPolicyNotFoundException::class.java) {
                pointPolicyService.updatePolicy(policyId, request)
            }
        }
    }

    @Nested
    @DisplayName("activatePolicy")
    inner class ActivatePolicy {
        @Test
        fun `포인트 정책 활성화 성공`() {
            // given
            val policyId = 1L
            val existingPolicy = PointPolicy.inactiveFixture()
            val activatedPolicy = existingPolicy.activate().withId(1L)

            whenever(pointPolicyRepository.findById(policyId)).thenReturn(Optional.of(existingPolicy))
            whenever(pointPolicyRepository.findByIsActiveTrue()).thenReturn(null)
            whenever(pointPolicyRepository.save(any<PointPolicy>())).thenReturn(activatedPolicy)

            // when
            val result = pointPolicyService.activatePolicy(policyId)

            // then
            assertTrue(result.isActive)
            verify(pointPolicyRepository).findById(policyId)
            verify(pointPolicyRepository).save(any())
        }

        @Test
        fun `기존 활성 정책이 있을 때 활성화하면 기존 정책이 비활성화됨`() {
            // given
            val policyId = 1L
            val existingPolicy = PointPolicy.inactiveFixture()
            val activatedPolicy = existingPolicy.activate().withId(1L)
            val existingActivePolicy = PointPolicy.activeFixture(policyName = "기존 활성 정책")

            whenever(pointPolicyRepository.findById(policyId)).thenReturn(Optional.of(existingPolicy))
            whenever(pointPolicyRepository.findByIsActiveTrue()).thenReturn(existingActivePolicy)
            whenever(pointPolicyRepository.save(any<PointPolicy>())).thenReturn(activatedPolicy)

            // when
            val result = pointPolicyService.activatePolicy(policyId)

            // then
            assertTrue(result.isActive)
            verify(pointPolicyRepository).findById(policyId)
            verify(pointPolicyRepository, times(2)).save(any()) // 기존 정책 비활성화 + 새 정책 활성화
        }
    }

    @Nested
    @DisplayName("deactivatePolicy")
    inner class DeactivatePolicy {
        @Test
        fun `포인트 정책 비활성화 성공`() {
            // given
            val policyId = 1L
            val existingPolicy = PointPolicy.activeFixture()
            val deactivatedPolicy = existingPolicy.deactivate().withId(1L)

            whenever(pointPolicyRepository.findById(policyId)).thenReturn(Optional.of(existingPolicy))
            whenever(pointPolicyRepository.save(any<PointPolicy>())).thenReturn(deactivatedPolicy)

            // when
            val result = pointPolicyService.deactivatePolicy(policyId)

            // then
            assertFalse(result.isActive)
            verify(pointPolicyRepository).findById(policyId)
            verify(pointPolicyRepository).save(any())
        }
    }

    @Nested
    @DisplayName("getCurrentPolicy")
    inner class GetCurrentPolicy {
        @Test
        fun `현재 활성화된 정책 조회 성공`() {
            // given
            val activePolicy = PointPolicy.activeFixture()

            whenever(pointPolicyRepository.findByIsActiveTrue()).thenReturn(activePolicy)

            // when
            val result = pointPolicyService.getCurrentPolicy()

            // then
            assertNotNull(result)
            assertEquals("활성 정책", result!!.policyName)
            assertTrue(result.isActive)
        }

        @Test
        fun `현재 활성화된 정책이 없는 경우`() {
            // given
            whenever(pointPolicyRepository.findByIsActiveTrue()).thenReturn(null)

            // when
            val result = pointPolicyService.getCurrentPolicy()

            // then
            assertNull(result)
        }
    }

    @Nested
    @DisplayName("getPolicyById")
    inner class GetPolicyById {
        @Test
        fun `특정 정책 조회 성공`() {
            // given
            val policyId = 1L
            val policy = PointPolicy.fixture()

            whenever(pointPolicyRepository.findById(policyId)).thenReturn(Optional.of(policy))

            // when
            val result = pointPolicyService.getPolicyById(policyId)

            // then
            assertEquals(policyId, result.id)
            assertEquals("테스트 정책", result.policyName)
        }

        @Test
        fun `존재하지 않는 정책 조회 시 예외 발생`() {
            // given
            val policyId = 999L

            whenever(pointPolicyRepository.findById(policyId)).thenReturn(Optional.empty())

            // when & then
            assertThrows(PointPolicyNotFoundException::class.java) {
                pointPolicyService.getPolicyById(policyId)
            }
        }
    }
} 