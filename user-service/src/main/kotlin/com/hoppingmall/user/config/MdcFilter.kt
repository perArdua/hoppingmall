package com.hoppingmall.user.config

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
@Order(Ordered.HIGHEST_PRECEDENCE)
class MdcFilter(
    @Value("\${spring.application.name}") private val serviceName: String
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val rawTraceId = request.getHeader(TRACE_ID_HEADER)
        val traceId = if (isValidTraceId(rawTraceId)) rawTraceId!! else generateTraceId()
        try {
            MDC.put(TRACE_ID_KEY, traceId)
            MDC.put(SERVICE_KEY, serviceName)
            request.getHeader("x-user-id")?.let { MDC.put(USER_ID_KEY, it) }
            response.setHeader(TRACE_ID_HEADER, traceId)
            filterChain.doFilter(request, response)
        } finally {
            MDC.clear()
        }
    }

    private fun isValidTraceId(traceId: String?): Boolean =
        traceId != null && traceId.length in 1..64 && traceId.matches(TRACE_ID_PATTERN)

    private fun generateTraceId(): String = UUID.randomUUID().toString().replace("-", "").take(16)

    companion object {
        const val TRACE_ID_KEY = "traceId"
        const val TRACE_ID_HEADER = "X-Trace-Id"
        const val USER_ID_KEY = "userId"
        const val SERVICE_KEY = "service"
        private val TRACE_ID_PATTERN = Regex("[a-zA-Z0-9\\-]+")
    }
}
