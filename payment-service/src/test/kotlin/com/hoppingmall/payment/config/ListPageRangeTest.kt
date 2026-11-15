package com.hoppingmall.payment.config

import com.hoppingmall.common.GlobalExceptionHandler
import com.hoppingmall.common.UserPrincipal
import com.hoppingmall.payment.payment.controller.PaymentController
import com.hoppingmall.payment.payment.service.PaymentCommandService
import com.hoppingmall.payment.payment.service.PaymentQueryService
import com.hoppingmall.payment.point.controller.PointController
import com.hoppingmall.payment.point.service.PointCommandService
import com.hoppingmall.payment.point.service.PointQueryService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.SliceImpl
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders

@DisplayName("결제·포인트 내 목록의 page·size 범위")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class ListPageRangeTest {

    private val paymentQueryService: PaymentQueryService = mock()
    private val pointQueryService: PointQueryService = mock()

    private val mockMvc: MockMvc = MockMvcBuilders
        .standaloneSetup(
            PaymentController(mock<PaymentCommandService>(), paymentQueryService),
            PointController(pointQueryService, mock<PointCommandService>())
        )
        .setControllerAdvice(GlobalExceptionHandler())
        .setCustomArgumentResolvers(AuthenticationPrincipalArgumentResolver())
        .build()

    @BeforeEach
    fun login() {
        val principal = UserPrincipal.of(USER_ID, "BUYER")
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(principal, null, principal.authorities)
    }

    @AfterEach
    fun logout() {
        SecurityContextHolder.clearContext()
    }

    @ParameterizedTest
    @CsvSource(
        "/api/v1/payments, size, 101",
        "/api/v1/payments, size, 1000000",
        "/api/v1/payments, size, 0",
        "/api/v1/payments, page, -1",
        "/api/v1/points/my-history, size, 101",
        "/api/v1/points/my-history, size, 1000000",
        "/api/v1/points/my-history, size, 0",
        "/api/v1/points/my-history, page, -1"
    )
    fun 범위를_벗어난_page나_size는_400과_그_인자_이름의_오류로_거절하고_조회하지_않는다(path: String, name: String, value: String) {
        mockMvc.get(path) { param(name, value) }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("C001") }
            jsonPath("$.errors[0].field") { value(name) }
        }

        verify(paymentQueryService, never()).getPaymentsByUserId(any(), any())
        verify(pointQueryService, never()).getPointHistory(any(), any())
    }

    @ParameterizedTest
    @CsvSource(
        "/api/v1/payments, 0, 1",
        "/api/v1/payments, 3, 100",
        "/api/v1/points/my-history, 0, 1",
        "/api/v1/points/my-history, 3, 100"
    )
    fun 범위_안의_page와_size는_그대로_조회한다(path: String, page: Int, size: Int) {
        val pageable = PageRequest.of(page, size)
        whenever(paymentQueryService.getPaymentsByUserId(eq(USER_ID), eq(pageable))).thenReturn(SliceImpl(emptyList(), pageable, false))
        whenever(pointQueryService.getPointHistory(eq(USER_ID), eq(pageable))).thenReturn(SliceImpl(emptyList(), pageable, false))

        mockMvc.get(path) {
            param("page", page.toString())
            param("size", size.toString())
        }.andExpect { status { isOk() } }

        if (path == "/api/v1/payments") {
            verify(paymentQueryService).getPaymentsByUserId(USER_ID, pageable)
        } else {
            verify(pointQueryService).getPointHistory(USER_ID, pageable)
        }
    }

    private companion object {
        const val USER_ID = 7L
    }
}
