package com.hoppingmall.common.config

import io.micrometer.tracing.Tracer
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
class MdcFilter(
    @Value("\${spring.application.name:unknown}") private val serviceName: String,
    private val tracer: Tracer
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        try {
            val otelTraceId = tracer.currentSpan()?.context()?.traceId()
                ?.takeIf { it.isNotBlank() }
            val rawTraceId = request.getHeader(TRACE_ID_HEADER)
            val traceId = otelTraceId
                ?: if (isValidTraceId(rawTraceId)) rawTraceId!! else generateTraceId()

            MDC.put(TRACE_ID_KEY, traceId)
            MDC.put(SERVICE_KEY, serviceName)
            request.getHeader(USER_ID_HEADER)?.let { MDC.put(USER_ID_KEY, it) }
            request.getHeader(GLOBAL_TRACE_ID_HEADER)?.let { MDC.put(GLOBAL_TRACE_ID_KEY, it) }
            response.setHeader(TRACE_ID_HEADER, traceId)
            filterChain.doFilter(request, response)
        } finally {
            MDC.remove(TRACE_ID_KEY)
            MDC.remove(SERVICE_KEY)
            MDC.remove(USER_ID_KEY)
            MDC.remove(GLOBAL_TRACE_ID_KEY)
        }
    }

    private fun isValidTraceId(traceId: String?): Boolean =
        traceId != null && traceId.length in 1..64 && traceId.matches(TRACE_ID_PATTERN)

    private fun generateTraceId(): String = UUID.randomUUID().toString().replace("-", "").take(16)

    companion object {
        const val TRACE_ID_KEY = "traceId"
        const val TRACE_ID_HEADER = "X-Trace-Id"
        const val USER_ID_KEY = "userId"
        const val USER_ID_HEADER = "X-User-Id"
        const val SERVICE_KEY = "service"
        const val SERVICE_HEADER = "X-Service-Name"
        const val GLOBAL_TRACE_ID_KEY = "globalTraceId"
        const val GLOBAL_TRACE_ID_HEADER = "X-Global-Trace-Id"
        private val TRACE_ID_PATTERN = Regex("[a-zA-Z0-9\\-]+")
    }
}
