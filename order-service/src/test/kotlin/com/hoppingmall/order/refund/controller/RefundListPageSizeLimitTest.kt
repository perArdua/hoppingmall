package com.hoppingmall.order.refund.controller

import com.hoppingmall.common.GlobalExceptionHandler
import com.hoppingmall.common.UserPrincipal
import com.hoppingmall.order.refund.service.RefundCommandService
import com.hoppingmall.order.refund.service.RefundQueryService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders

@DisplayName("환불 목록의 page·size 범위")
@DisplayNameGeneration(ReplaceUnderscores::class)
class RefundListPageSizeLimitTest {

    private val refundCommandService: RefundCommandService = mock()
    private val refundQueryService: RefundQueryService = mock()
    private val mockMvc: MockMvc = MockMvcBuilders.standaloneSetup(RefundController(refundCommandService, refundQueryService))
        .setCustomArgumentResolvers(AuthenticationPrincipalArgumentResolver())
        .setControllerAdvice(GlobalExceptionHandler())
        .build()

    @BeforeEach
    fun setUp() {
        val principal = UserPrincipal.of(7L, "SELLER")
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(principal, null, principal.authorities)
    }

    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clearContext()
    }

    @ParameterizedTest
    @CsvSource(
        "/api/v1/refunds/my,page,-1",
        "/api/v1/refunds/my,size,0",
        "/api/v1/refunds/my,size,101",
        "/api/v1/refunds/seller,page,-1",
        "/api/v1/refunds/seller,size,0",
        "/api/v1/refunds/seller,size,101"
    )
    fun 범위_밖_값은_조회_없이_400과_필드_오류다(path: String, field: String, value: String) {
        mockMvc.get(path) { param(field, value) }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("C001") }
            jsonPath("$.errors[0].field") { value(field) }
        }
        verifyNoInteractions(refundQueryService)
    }
}
