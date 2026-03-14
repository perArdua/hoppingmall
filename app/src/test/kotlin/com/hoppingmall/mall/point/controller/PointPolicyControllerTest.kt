package com.hoppingmall.mall.point.controller

import com.hoppingmall.mall.point.dto.request.PointPolicyRequest
import com.hoppingmall.mall.point.dto.response.PointPolicyResponse
import com.hoppingmall.mall.point.service.PointPolicyService
import org.junit.jupiter.api.*
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Assertions.assertEquals
import org.mockito.kotlin.*
import org.springframework.http.HttpStatus
import java.math.BigDecimal

@DisplayName("PointPolicyController")
@DisplayNameGeneration(ReplaceUnderscores::class)
class PointPolicyControllerTest {

    private val pointPolicyService: PointPolicyService = mock()
    private val pointPolicyController = PointPolicyController(pointPolicyService)

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
            val expectedResponse = PointPolicyResponse(
                id = 1L,
                policyName = "테스트 정책",
                earnRate = BigDecimal("0.01"),
                maxEarnRate = BigDecimal("0.02"),
                minUseAmount = BigDecimal("1000"),
                maxUseAmount = BigDecimal("10000"),
                isActive = true,
                description = "테스트용 정책"
            )

            whenever(pointPolicyService.createPolicy(request)).thenReturn(expectedResponse)

            // when
            val result = pointPolicyController.createPolicy(request)

            // then
            assertEquals(HttpStatus.OK, result.statusCode)
            assertEquals(expectedResponse, result.body)
            verify(pointPolicyService).createPolicy(request)
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
            val expectedResponse = PointPolicyResponse(
                id = policyId,
                policyName = "수정된 정책",
                earnRate = BigDecimal("0.02"),
                maxEarnRate = BigDecimal("0.03"),
                minUseAmount = BigDecimal("2000"),
                maxUseAmount = BigDecimal("20000"),
                isActive = true,
                description = "수정된 정책"
            )

            whenever(pointPolicyService.updatePolicy(policyId, request)).thenReturn(expectedResponse)

            // when
            val result = pointPolicyController.updatePolicy(policyId, request)

            // then
            assertEquals(HttpStatus.OK, result.statusCode)
            assertEquals(expectedResponse, result.body)
            verify(pointPolicyService).updatePolicy(policyId, request)
        }
    }

    @Nested
    @DisplayName("activatePolicy")
    inner class ActivatePolicy {
        @Test
        fun `포인트 정책 활성화 성공`() {
            // given
            val policyId = 1L
            val expectedResponse = PointPolicyResponse(
                id = policyId,
                policyName = "활성화된 정책",
                earnRate = BigDecimal("0.01"),
                maxEarnRate = BigDecimal("0.02"),
                minUseAmount = BigDecimal("1000"),
                maxUseAmount = BigDecimal("10000"),
                isActive = true,
                description = "활성화된 정책"
            )

            whenever(pointPolicyService.activatePolicy(policyId)).thenReturn(expectedResponse)

            // when
            val result = pointPolicyController.activatePolicy(policyId)

            // then
            assertEquals(HttpStatus.OK, result.statusCode)
            assertEquals(expectedResponse, result.body)
            verify(pointPolicyService).activatePolicy(policyId)
        }
    }

    @Nested
    @DisplayName("deactivatePolicy")
    inner class DeactivatePolicy {
        @Test
        fun `포인트 정책 비활성화 성공`() {
            // given
            val policyId = 1L
            val expectedResponse = PointPolicyResponse(
                id = policyId,
                policyName = "비활성화된 정책",
                earnRate = BigDecimal("0.01"),
                maxEarnRate = BigDecimal("0.02"),
                minUseAmount = BigDecimal("1000"),
                maxUseAmount = BigDecimal("10000"),
                isActive = false,
                description = "비활성화된 정책"
            )

            whenever(pointPolicyService.deactivatePolicy(policyId)).thenReturn(expectedResponse)

            // when
            val result = pointPolicyController.deactivatePolicy(policyId)

            // then
            assertEquals(HttpStatus.OK, result.statusCode)
            assertEquals(expectedResponse, result.body)
            verify(pointPolicyService).deactivatePolicy(policyId)
        }
    }

    @Nested
    @DisplayName("getCurrentPolicy")
    inner class GetCurrentPolicy {
        @Test
        fun `현재 활성화된 정책 조회 성공`() {
            // given
            val expectedResponse = PointPolicyResponse(
                id = 1L,
                policyName = "현재 정책",
                earnRate = BigDecimal("0.01"),
                maxEarnRate = BigDecimal("0.02"),
                minUseAmount = BigDecimal("1000"),
                maxUseAmount = BigDecimal("10000"),
                isActive = true,
                description = "현재 활성화된 정책"
            )

            whenever(pointPolicyService.getCurrentPolicy()).thenReturn(expectedResponse)

            // when
            val result = pointPolicyController.getCurrentPolicy()

            // then
            assertEquals(HttpStatus.OK, result.statusCode)
            assertEquals(expectedResponse, result.body)
            verify(pointPolicyService).getCurrentPolicy()
        }

        @Test
        fun `현재 활성화된 정책이 없는 경우`() {
            // given
            whenever(pointPolicyService.getCurrentPolicy()).thenReturn(null)

            // when
            val result = pointPolicyController.getCurrentPolicy()

            // then
            assertEquals(HttpStatus.OK, result.statusCode)
            assertEquals(null, result.body)
            verify(pointPolicyService).getCurrentPolicy()
        }
    }

    @Nested
    @DisplayName("getPolicyById")
    inner class GetPolicyById {
        @Test
        fun `특정 정책 조회 성공`() {
            // given
            val policyId = 1L
            val expectedResponse = PointPolicyResponse(
                id = policyId,
                policyName = "특정 정책",
                earnRate = BigDecimal("0.01"),
                maxEarnRate = BigDecimal("0.02"),
                minUseAmount = BigDecimal("1000"),
                maxUseAmount = BigDecimal("10000"),
                isActive = true,
                description = "특정 정책"
            )

            whenever(pointPolicyService.getPolicyById(policyId)).thenReturn(expectedResponse)

            // when
            val result = pointPolicyController.getPolicyById(policyId)

            // then
            assertEquals(HttpStatus.OK, result.statusCode)
            assertEquals(expectedResponse, result.body)
            verify(pointPolicyService).getPolicyById(policyId)
        }
    }
} 