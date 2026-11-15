package com.hoppingmall.dlq.controller

import com.hoppingmall.common.GlobalExceptionHandler
import com.hoppingmall.dlq.domain.DLQStatus
import com.hoppingmall.dlq.service.DLQCommandService
import com.hoppingmall.dlq.service.DLQQueryService
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders

@DisplayName("DLQ 메시지 목록의 page·size 범위")
@DisplayNameGeneration(ReplaceUnderscores::class)
class DLQControllerPageSizeLimitTest {

    private val dlqCommandService: DLQCommandService = mock()
    private val dlqQueryService: DLQQueryService = mock()
    private val mockMvc: MockMvc = MockMvcBuilders.standaloneSetup(DLQController(dlqCommandService, dlqQueryService))
        .setControllerAdvice(GlobalExceptionHandler())
        .build()

    @ParameterizedTest
    @CsvSource("page,-1", "size,0", "size,101")
    fun 토픽별_목록의_범위_밖_값은_400과_필드_오류다(field: String, value: String) {
        mockMvc.get("/api/v1/admin/dlq/messages/payment") { param(field, value) }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("C001") }
            jsonPath("$.errors[0].field") { value(field) }
        }
        verify(dlqQueryService, never()).getDLQMessages(any(), any(), any())
    }

    @ParameterizedTest
    @CsvSource("page,-1", "size,0", "size,101")
    fun 상태별_목록의_범위_밖_값은_400과_필드_오류다(field: String, value: String) {
        mockMvc.get("/api/v1/admin/dlq/messages") {
            param("status", "PENDING")
            param(field, value)
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("C001") }
            jsonPath("$.errors[0].field") { value(field) }
        }
        verify(dlqQueryService, never()).getDLQMessagesByStatus(any(), any(), any())
    }

    @Test
    fun 상한인_size_100은_받는다() {
        whenever(dlqQueryService.getDLQMessagesByStatus(DLQStatus.PENDING, 0, 100)).thenReturn(PageImpl(emptyList(), PageRequest.of(0, 100), 0))

        mockMvc.get("/api/v1/admin/dlq/messages") {
            param("status", "PENDING")
            param("size", "100")
        }.andExpect { status { isOk() } }
    }
}
