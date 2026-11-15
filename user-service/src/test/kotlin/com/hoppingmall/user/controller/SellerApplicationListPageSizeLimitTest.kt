package com.hoppingmall.user.controller

import com.hoppingmall.common.GlobalExceptionHandler
import com.hoppingmall.user.service.AdminCommandService
import com.hoppingmall.user.service.SellerQueryService
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders

@DisplayName("관리자 판매자 신청 목록의 page·size 범위")
@DisplayNameGeneration(ReplaceUnderscores::class)
class SellerApplicationListPageSizeLimitTest {

    private val adminCommandService: AdminCommandService = mock()
    private val sellerQueryService: SellerQueryService = mock()
    private val mockMvc: MockMvc = MockMvcBuilders.standaloneSetup(AdminController(adminCommandService, sellerQueryService))
        .setControllerAdvice(GlobalExceptionHandler())
        .build()

    @ParameterizedTest
    @CsvSource("page,-1", "size,0", "size,101")
    fun 범위_밖_값은_조용히_줄이지_않고_400과_필드_오류다(field: String, value: String) {
        mockMvc.get("/api/v1/admin/sellers") { param(field, value) }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("C001") }
            jsonPath("$.errors[0].field") { value(field) }
        }
        verifyNoInteractions(sellerQueryService)
    }
}
