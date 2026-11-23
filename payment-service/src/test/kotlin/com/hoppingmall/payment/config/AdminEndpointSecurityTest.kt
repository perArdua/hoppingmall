package com.hoppingmall.payment.config

import com.hoppingmall.dlq.controller.DLQController
import com.hoppingmall.dlq.dto.DLQStatsResponse
import com.hoppingmall.dlq.service.DLQCommandService
import com.hoppingmall.dlq.service.DLQQueryService
import com.hoppingmall.payment.coupon.controller.CouponController
import com.hoppingmall.payment.coupon.dto.response.CouponResponse
import com.hoppingmall.payment.coupon.enum.CouponStatus
import com.hoppingmall.payment.coupon.enum.DiscountType
import com.hoppingmall.payment.coupon.service.CouponCommandService
import com.hoppingmall.payment.coupon.service.CouponQueryService
import io.micrometer.tracing.Tracer
import org.hamcrest.Matchers.containsInAnyOrder
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import java.math.BigDecimal
import java.time.LocalDateTime

@DisplayName("payment 관리자 엔드포인트 권한")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@WebMvcTest(controllers = [CouponController::class, DLQController::class])
@Import(SecurityConfig::class)
class AdminEndpointSecurityTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var couponCommandService: CouponCommandService

    @MockitoBean
    private lateinit var couponQueryService: CouponQueryService

    @MockitoBean
    private lateinit var dlqCommandService: DLQCommandService

    @MockitoBean
    private lateinit var dlqQueryService: DLQQueryService

    @MockitoBean
    private lateinit var tracer: Tracer

    private val couponJson = """
        {"name":"관리자 쿠폰","discount_type":"FIXED","discount_value":1000,"min_order_amount":0,"total_quantity":10,"valid_from":"2026-01-01T00:00:00","valid_until":"2026-12-31T23:59:59"}
    """.trimIndent()

    @Nested
    @DisplayName("쿠폰 관리 API")
    inner class AdminCoupons {

        @Test
        fun BUYER_역할로_쿠폰_생성_시_403_응답() {
            mockMvc.post("/api/v1/admin/coupons") {
                contentType = MediaType.APPLICATION_JSON
                content = couponJson
                header("X-User-Id", "1")
                header("x-user-role", "BUYER")
            }.andExpect { status { isForbidden() } }
        }

        @Test
        fun SELLER_역할로_쿠폰_생성_시_403_응답() {
            mockMvc.post("/api/v1/admin/coupons") {
                contentType = MediaType.APPLICATION_JSON
                content = couponJson
                header("X-User-Id", "1")
                header("x-user-role", "SELLER")
            }.andExpect { status { isForbidden() } }
        }

        @Test
        fun BUYER_역할로_쿠폰_목록_조회_시_403_응답() {
            mockMvc.get("/api/v1/admin/coupons") {
                header("X-User-Id", "1")
                header("x-user-role", "BUYER")
            }.andExpect { status { isForbidden() } }
        }

        @Test
        fun BUYER_역할로_쿠폰_상태_변경_시_403_응답() {
            mockMvc.patch("/api/v1/admin/coupons/1/status?status=INACTIVE") {
                header("X-User-Id", "1")
                header("x-user-role", "BUYER")
            }.andExpect { status { isForbidden() } }
        }

        @Test
        fun ADMIN_역할로_쿠폰_목록_조회_성공() {
            whenever(couponQueryService.getAllCoupons()).thenReturn(emptyList())

            mockMvc.get("/api/v1/admin/coupons") {
                header("X-User-Id", "1")
                header("x-user-role", "ADMIN")
            }.andExpect { status { isOk() } }
        }

        @Test
        fun ADMIN_역할로_100을_넘는_비율_할인과_거꾸로_된_기간의_쿠폰을_만들면_400과_필드_오류() {
            mockMvc.post("/api/v1/admin/coupons") {
                contentType = MediaType.APPLICATION_JSON
                content = """
                    {"name":"비율 쿠폰","code":"PCT-150","discount_type":"PERCENTAGE","discount_value":150,
                    "min_order_amount":0,"total_quantity":10,
                    "valid_from":"2026-12-31T00:00:00","valid_to":"2026-12-01T00:00:00"}
                """.trimIndent()
                header("X-User-Id", "1")
                header("x-user-role", "ADMIN")
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("C001") }
                jsonPath("$.errors[*].field") { value(containsInAnyOrder("discount_value", "valid_to")) }
            }
        }

        @Test
        fun 사용_가능한_쿠폰에_내가_받은_쿠폰인지가_담긴다() {
            whenever(couponQueryService.getAvailableCoupons()).thenReturn(listOf(coupon(1L), coupon(2L)))
            whenever(couponQueryService.getIssuedCouponIds(7L, listOf(1L, 2L))).thenReturn(setOf(2L))

            mockMvc.get("/api/v1/coupons/available") {
                header("X-User-Id", "7")
                header("x-user-role", "BUYER")
            }.andExpect {
                status { isOk() }
                jsonPath("$.data[0].id") { value(1) }
                jsonPath("$.data[0].already_issued") { value(false) }
                jsonPath("$.data[1].id") { value(2) }
                jsonPath("$.data[1].already_issued") { value(true) }
            }
        }

        @Test
        fun BUYER_역할로_사용_가능한_쿠폰_조회_성공() {
            whenever(couponQueryService.getAvailableCoupons()).thenReturn(emptyList())

            mockMvc.get("/api/v1/coupons/available") {
                header("X-User-Id", "1")
                header("x-user-role", "BUYER")
            }.andExpect { status { isOk() } }
        }
    }

    private fun coupon(id: Long) = CouponResponse(
        id = id,
        name = "쿠폰 $id",
        code = "CODE-$id",
        discountType = DiscountType.FIXED_AMOUNT,
        discountValue = BigDecimal("1000"),
        minOrderAmount = BigDecimal.ZERO,
        maxDiscountAmount = null,
        totalQuantity = 10,
        issuedQuantity = 1,
        validFrom = LocalDateTime.of(2026, 1, 1, 0, 0),
        validTo = LocalDateTime.of(2026, 12, 31, 0, 0),
        status = CouponStatus.ACTIVE,
        createdAt = LocalDateTime.of(2026, 1, 1, 0, 0)
    )

    @Nested
    @DisplayName("DLQ 관리 API")
    inner class AdminDlq {

        @Test
        fun BUYER_역할로_DLQ_통계_조회_시_403_응답() {
            mockMvc.get("/api/v1/admin/dlq/stats") {
                header("X-User-Id", "1")
                header("x-user-role", "BUYER")
            }.andExpect { status { isForbidden() } }
        }

        @Test
        fun BUYER_역할로_DLQ_토픽_재처리_시_403_응답() {
            mockMvc.post("/api/v1/admin/dlq/retry/payment") {
                header("X-User-Id", "1")
                header("x-user-role", "BUYER")
            }.andExpect { status { isForbidden() } }
        }

        @Test
        fun BUYER_역할로_DLQ_메시지_재처리_시_403_응답() {
            mockMvc.post("/api/v1/admin/dlq/retry/message/1") {
                header("X-User-Id", "1")
                header("x-user-role", "BUYER")
            }.andExpect { status { isForbidden() } }
        }

        @Test
        fun BUYER_역할로_DLQ_메시지_삭제_시_403_응답() {
            mockMvc.delete("/api/v1/admin/dlq/clear/payment") {
                header("X-User-Id", "1")
                header("x-user-role", "BUYER")
            }.andExpect { status { isForbidden() } }
        }

        @Test
        fun ADMIN_역할로_DLQ_통계_조회_성공() {
            whenever(dlqQueryService.getDLQStats()).thenReturn(DLQStatsResponse(0L, 0L, 0L, 0L, emptyList(), 0L))

            mockMvc.get("/api/v1/admin/dlq/stats") {
                header("X-User-Id", "1")
                header("x-user-role", "ADMIN")
            }.andExpect { status { isOk() } }
        }

        @Test
        fun 인증되지_않은_DLQ_통계_조회_시_403_응답() {
            mockMvc.get("/api/v1/admin/dlq/stats")
                .andExpect { status { isForbidden() } }
        }
    }
}
