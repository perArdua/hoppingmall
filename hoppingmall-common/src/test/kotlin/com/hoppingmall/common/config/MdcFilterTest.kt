package com.hoppingmall.common.config

import io.micrometer.tracing.Span
import io.micrometer.tracing.TraceContext
import io.micrometer.tracing.Tracer
import jakarta.servlet.FilterChain
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever
import org.slf4j.MDC
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

@DisplayName("MdcFilter 단위 테스트")
@DisplayNameGeneration(ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class MdcFilterTest {

    @Mock
    private lateinit var tracer: Tracer

    @Mock
    private lateinit var span: Span

    @Mock
    private lateinit var traceContext: TraceContext

    @Mock
    private lateinit var filterChain: FilterChain

    private lateinit var mdcFilter: MdcFilter
    private lateinit var request: MockHttpServletRequest
    private lateinit var response: MockHttpServletResponse

    @BeforeEach
    fun setUp() {
        mdcFilter = MdcFilter("test-service", tracer)
        request = MockHttpServletRequest()
        response = MockHttpServletResponse()
        MDC.clear()
    }

    @Test
    fun OTel_traceId가_있으면_해당_값을_MDC에_설정한다() {
        whenever(tracer.currentSpan()).thenReturn(span)
        whenever(span.context()).thenReturn(traceContext)
        whenever(traceContext.traceId()).thenReturn("abc123def456")

        var capturedTraceId: String? = null
        mdcFilter.doFilter(request, response, FilterChain { _, _ ->
            capturedTraceId = MDC.get("traceId")
        })

        assertThat(capturedTraceId).isEqualTo("abc123def456")
        assertThat(response.getHeader("X-Trace-Id")).isEqualTo("abc123def456")
    }

    @Test
    fun OTel_traceId가_없으면_헤더의_traceId를_사용한다() {
        whenever(tracer.currentSpan()).thenReturn(null)
        request.addHeader("X-Trace-Id", "header-trace-123")

        var capturedTraceId: String? = null
        mdcFilter.doFilter(request, response, FilterChain { _, _ ->
            capturedTraceId = MDC.get("traceId")
        })

        assertThat(capturedTraceId).isEqualTo("header-trace-123")
    }

    @Test
    fun OTel과_헤더_모두_없으면_UUID_traceId를_생성한다() {
        whenever(tracer.currentSpan()).thenReturn(null)

        var capturedTraceId: String? = null
        mdcFilter.doFilter(request, response, FilterChain { _, _ ->
            capturedTraceId = MDC.get("traceId")
        })

        assertThat(capturedTraceId).isNotNull()
        assertThat(capturedTraceId).hasSize(16)
    }

    @Test
    fun service_이름이_MDC에_설정된다() {
        whenever(tracer.currentSpan()).thenReturn(null)

        var capturedService: String? = null
        mdcFilter.doFilter(request, response, FilterChain { _, _ ->
            capturedService = MDC.get("service")
        })

        assertThat(capturedService).isEqualTo("test-service")
    }

    @Test
    fun userId_헤더가_있으면_MDC에_설정된다() {
        whenever(tracer.currentSpan()).thenReturn(null)
        request.addHeader("x-user-id", "42")

        var capturedUserId: String? = null
        mdcFilter.doFilter(request, response, FilterChain { _, _ ->
            capturedUserId = MDC.get("userId")
        })

        assertThat(capturedUserId).isEqualTo("42")
    }

    @Test
    fun 필터_완료_후_MDC에서_관리_키만_제거된다() {
        whenever(tracer.currentSpan()).thenReturn(null)

        MDC.put("externalKey", "should-survive")

        mdcFilter.doFilter(request, response, FilterChain { _, _ -> })

        assertThat(MDC.get("traceId")).isNull()
        assertThat(MDC.get("service")).isNull()
        assertThat(MDC.get("userId")).isNull()
        assertThat(MDC.get("externalKey")).isEqualTo("should-survive")

        MDC.clear()
    }
}
