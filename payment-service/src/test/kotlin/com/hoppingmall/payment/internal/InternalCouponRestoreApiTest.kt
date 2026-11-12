package com.hoppingmall.payment.internal

import com.hoppingmall.common.config.GatewayHeaderAuthenticationFilter
import com.hoppingmall.common.config.InternalTokenFilter
import com.hoppingmall.payment.config.SecurityConfig
import com.hoppingmall.payment.coupon.service.CouponCommandService
import com.hoppingmall.payment.coupon.service.CouponQueryService
import com.hoppingmall.payment.payment.domain.repository.PaymentRepository
import com.hoppingmall.payment.payment.service.PaymentCommandService
import com.hoppingmall.payment.point.domain.Point
import com.hoppingmall.payment.point.domain.PointHistoryRepository
import com.hoppingmall.payment.point.domain.PointRepository
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.FilterType
import org.springframework.context.annotation.Import
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

@DisplayName("내부 쿠폰 복원 API")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@WebMvcTest(
    useDefaultFilters = false,
    includeFilters = [
        ComponentScan.Filter(
            type = FilterType.REGEX,
            pattern = ["com\\.hoppingmall\\.payment\\.internal\\.Internal.*Controller"]
        )
    ]
)
@Import(SecurityConfig::class, GatewayHeaderAuthenticationFilter::class, InternalTokenFilter::class)
class InternalCouponRestoreApiTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Value("\${internal.service.token}")
    private lateinit var internalToken: String

    @MockitoBean
    private lateinit var couponCommandService: CouponCommandService

    @MockitoBean
    private lateinit var couponQueryService: CouponQueryService

    @MockitoBean
    private lateinit var paymentRepository: PaymentRepository

    @MockitoBean
    private lateinit var paymentCommandService: PaymentCommandService

    @MockitoBean
    private lateinit var pointRepository: PointRepository

    @MockitoBean
    private lateinit var pointHistoryRepository: PointHistoryRepository

    @Test
    fun 내부_토큰이_있어도_쿠폰_복원_요청은_404이고_쿠폰을_되돌리지_않는다() {
        mockMvc.post("/internal/api/v1/coupons/1/restore?userId=10") {
            header("X-Internal-Token", internalToken)
        }.andExpect { status { isNotFound() } }

        verify(couponCommandService, never()).restoreCouponByPayment(any(), any())
    }

    @Test
    fun 다른_내부_API는_내부_토큰으로_그대로_응답한다() {
        whenever(pointRepository.findByUserId(10L)).thenReturn(Point(userId = 10L))

        mockMvc.get("/internal/api/v1/points/by-user/10/balance") {
            header("X-Internal-Token", internalToken)
        }.andExpect { status { isOk() } }
    }
}
