package com.hoppingmall.payment.point.controller

import com.hoppingmall.payment.config.SecurityConfig
import com.hoppingmall.payment.point.dto.response.PointPolicyResponse
import com.hoppingmall.payment.point.service.PointPolicyService
import io.micrometer.tracing.Tracer
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.SliceImpl
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import java.math.BigDecimal

@DisplayName("포인트 정책 엔드포인트 권한")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@WebMvcTest(PointPolicyController::class)
@Import(SecurityConfig::class)
class PointPolicyControllerSecurityTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var pointPolicyService: PointPolicyService

    @MockitoBean
    private lateinit var tracer: Tracer

    private val policyJson = """
        {"policy_name":"기본 정책","earn_rate":0.01,"max_earn_rate":0.05,"min_use_amount":1000,"max_use_amount":50000}
    """.trimIndent()

    private val policyResponse = PointPolicyResponse(
        id = 1L,
        policyName = "기본 정책",
        earnRate = BigDecimal("0.01"),
        maxEarnRate = BigDecimal("0.05"),
        minUseAmount = BigDecimal("1000"),
        maxUseAmount = BigDecimal("50000"),
        isActive = true,
        description = null
    )

    @Nested
    @DisplayName("POST /api/v1/point-policies")
    inner class CreatePolicy {

        @Test
        fun BUYER_역할로_포인트_정책_생성_시_403_응답() {
            mockMvc.post("/api/v1/point-policies") {
                contentType = MediaType.APPLICATION_JSON
                content = policyJson
                header("X-User-Id", "1")
                header("x-user-role", "BUYER")
            }.andExpect { status { isForbidden() } }
        }

        @Test
        fun SELLER_역할로_포인트_정책_생성_시_403_응답() {
            mockMvc.post("/api/v1/point-policies") {
                contentType = MediaType.APPLICATION_JSON
                content = policyJson
                header("X-User-Id", "1")
                header("x-user-role", "SELLER")
            }.andExpect { status { isForbidden() } }
        }

        @Test
        fun ADMIN_역할로_포인트_정책_생성_성공() {
            whenever(pointPolicyService.createPolicy(any())).thenReturn(policyResponse)

            mockMvc.post("/api/v1/point-policies") {
                contentType = MediaType.APPLICATION_JSON
                content = policyJson
                header("X-User-Id", "1")
                header("x-user-role", "ADMIN")
            }.andExpect { status { isOk() } }
        }
    }

    @Nested
    @DisplayName("PUT /api/v1/point-policies/{policyId}")
    inner class UpdatePolicy {

        @Test
        fun BUYER_역할로_포인트_정책_수정_시_403_응답() {
            mockMvc.put("/api/v1/point-policies/1") {
                contentType = MediaType.APPLICATION_JSON
                content = policyJson
                header("X-User-Id", "1")
                header("x-user-role", "BUYER")
            }.andExpect { status { isForbidden() } }
        }

        @Test
        fun ADMIN_역할로_포인트_정책_수정_성공() {
            whenever(pointPolicyService.updatePolicy(eq(1L), any())).thenReturn(policyResponse)

            mockMvc.put("/api/v1/point-policies/1") {
                contentType = MediaType.APPLICATION_JSON
                content = policyJson
                header("X-User-Id", "1")
                header("x-user-role", "ADMIN")
            }.andExpect { status { isOk() } }
        }
    }

    @Nested
    @DisplayName("POST /api/v1/point-policies/{policyId}/activate, deactivate")
    inner class ChangeActivation {

        @Test
        fun BUYER_역할로_포인트_정책_활성화_시_403_응답() {
            mockMvc.post("/api/v1/point-policies/1/activate") {
                header("X-User-Id", "1")
                header("x-user-role", "BUYER")
            }.andExpect { status { isForbidden() } }
        }

        @Test
        fun BUYER_역할로_포인트_정책_비활성화_시_403_응답() {
            mockMvc.post("/api/v1/point-policies/1/deactivate") {
                header("X-User-Id", "1")
                header("x-user-role", "BUYER")
            }.andExpect { status { isForbidden() } }
        }

        @Test
        fun ADMIN_역할로_포인트_정책_활성화_성공() {
            whenever(pointPolicyService.activatePolicy(1L)).thenReturn(policyResponse)

            mockMvc.post("/api/v1/point-policies/1/activate") {
                header("X-User-Id", "1")
                header("x-user-role", "ADMIN")
            }.andExpect { status { isOk() } }
        }

        @Test
        fun ADMIN_역할로_포인트_정책_비활성화_성공() {
            whenever(pointPolicyService.deactivatePolicy(1L)).thenReturn(policyResponse.copy(isActive = false))

            mockMvc.post("/api/v1/point-policies/1/deactivate") {
                header("X-User-Id", "1")
                header("x-user-role", "ADMIN")
            }.andExpect { status { isOk() } }
        }
    }

    @Nested
    @DisplayName("GET /api/v1/point-policies")
    inner class ReadPolicy {

        @Test
        fun BUYER_역할로_현재_포인트_정책_조회_성공() {
            whenever(pointPolicyService.getCurrentPolicy()).thenReturn(policyResponse)

            mockMvc.get("/api/v1/point-policies/current") {
                header("X-User-Id", "1")
                header("x-user-role", "BUYER")
            }.andExpect { status { isOk() } }
        }

        @Test
        fun BUYER_역할로_포인트_정책_단건_조회_성공() {
            whenever(pointPolicyService.getPolicyById(1L)).thenReturn(policyResponse)

            mockMvc.get("/api/v1/point-policies/1") {
                header("X-User-Id", "1")
                header("x-user-role", "BUYER")
            }.andExpect { status { isOk() } }
        }

        @Test
        fun 인증되지_않은_포인트_정책_조회_시_403_응답() {
            mockMvc.get("/api/v1/point-policies/current")
                .andExpect { status { isForbidden() } }
        }
    }

    @Nested
    @DisplayName("GET /api/v1/point-policies 목록")
    inner class ListPolicies {

        @Test
        fun ADMIN_역할로_포인트_정책_목록_조회_성공() {
            whenever(pointPolicyService.getPolicies(PageRequest.of(0, 20)))
                .thenReturn(SliceImpl(listOf(policyResponse), PageRequest.of(0, 20), false))

            mockMvc.get("/api/v1/point-policies") {
                header("X-User-Id", "1")
                header("x-user-role", "ADMIN")
            }.andExpect {
                status { isOk() }
                jsonPath("$.data.content[0].id") { value(1) }
            }
        }

        @Test
        fun BUYER_역할로_포인트_정책_목록_조회_시_403_응답() {
            mockMvc.get("/api/v1/point-policies") {
                header("X-User-Id", "1")
                header("x-user-role", "BUYER")
            }.andExpect { status { isForbidden() } }
        }

        @Test
        fun 목록_size가_상한을_넘으면_400_응답() {
            mockMvc.get("/api/v1/point-policies") {
                param("size", "101")
                header("X-User-Id", "1")
                header("x-user-role", "ADMIN")
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.errors[0].field") { value("size") }
            }
        }

        @Test
        fun 목록_page가_음수이면_400_응답() {
            mockMvc.get("/api/v1/point-policies") {
                param("page", "-1")
                header("X-User-Id", "1")
                header("x-user-role", "ADMIN")
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.errors[0].field") { value("page") }
            }
        }
    }
}
