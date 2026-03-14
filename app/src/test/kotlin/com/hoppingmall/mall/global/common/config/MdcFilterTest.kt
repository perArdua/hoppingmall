package com.hoppingmall.mall.global.common.config

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.FilterChain
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.slf4j.MDC
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import java.util.Base64

@DisplayName("MdcFilter")
@DisplayNameGeneration(ReplaceUnderscores::class)
class MdcFilterTest {

    private val objectMapper = ObjectMapper()
    private val filter = MdcFilter(objectMapper, "monolith")

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
            assertThat(MDC.get("userId")).isNull()
            assertThat(MDC.get("service")).isNull()
        }

        @Test
        fun 서비스_이름이_MDC에_설정된다() {
            val request = MockHttpServletRequest()
            val response = MockHttpServletResponse()
            var capturedService: String? = null

            val filterChain = FilterChain { _, _ ->
                capturedService = MDC.get("service")
            }

            filter.doFilter(request, response, filterChain)

            assertThat(capturedService).isEqualTo("monolith")
        }

        @Test
        fun JWT_토큰이_있으면_userId가_MDC에_설정된다() {
            val payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("""{"sub":"42"}""".toByteArray())
            val fakeToken = "header.$payload.signature"

            val request = MockHttpServletRequest()
            request.addHeader("Authorization", "Bearer $fakeToken")
            val response = MockHttpServletResponse()
            var capturedUserId: String? = null

            val filterChain = FilterChain { _, _ ->
                capturedUserId = MDC.get("userId")
            }

            filter.doFilter(request, response, filterChain)

            assertThat(capturedUserId).isEqualTo("42")
        }

        @Test
        fun JWT_토큰이_없으면_userId가_설정되지_않는다() {
            val request = MockHttpServletRequest()
            val response = MockHttpServletResponse()
            var capturedUserId: String? = null

            val filterChain = FilterChain { _, _ ->
                capturedUserId = MDC.get("userId")
            }

            filter.doFilter(request, response, filterChain)

            assertThat(capturedUserId).isNull()
        }
    }
}
