package com.hoppingmall.mall.global.common.config

import jakarta.servlet.FilterChain
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.slf4j.MDC
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

@DisplayName("MdcFilter")
@DisplayNameGeneration(ReplaceUnderscores::class)
class MdcFilterTest {

    private val filter = MdcFilter()

    @AfterEach
    fun tearDown() {
        MDC.clear()
    }

    @Nested
    @DisplayName("doFilterInternal")
    inner class DoFilterInternal {

        @Test
        fun 요청_시_traceId가_MDC에_설정되고_응답_헤더에_포함된다() {
            val request = MockHttpServletRequest()
            val response = MockHttpServletResponse()
            var capturedTraceId: String? = null

            val filterChain = FilterChain { _, _ ->
                capturedTraceId = MDC.get("traceId")
            }

            filter.doFilter(request, response, filterChain)

            assertThat(capturedTraceId).isNotNull()
            assertThat(capturedTraceId).hasSize(16)
            assertThat(response.getHeader("X-Trace-Id")).isEqualTo(capturedTraceId)
            assertThat(MDC.get("traceId")).isNull()
        }

        @Test
        fun X_Trace_Id_헤더가_있으면_해당_값을_사용한다() {
            val request = MockHttpServletRequest()
            request.addHeader("X-Trace-Id", "external-trace-id")
            val response = MockHttpServletResponse()
            var capturedTraceId: String? = null

            val filterChain = FilterChain { _, _ ->
                capturedTraceId = MDC.get("traceId")
            }

            filter.doFilter(request, response, filterChain)

            assertThat(capturedTraceId).isEqualTo("external-trace-id")
            assertThat(response.getHeader("X-Trace-Id")).isEqualTo("external-trace-id")
        }

        @Test
        fun 필터_체인_완료_후_MDC가_정리된다() {
            val request = MockHttpServletRequest()
            val response = MockHttpServletResponse()
            val filterChain = FilterChain { _, _ -> }

            filter.doFilter(request, response, filterChain)

            assertThat(MDC.get("traceId")).isNull()
        }
    }
}
