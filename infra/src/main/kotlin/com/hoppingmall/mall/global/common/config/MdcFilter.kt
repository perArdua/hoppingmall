package com.hoppingmall.mall.global.common.config

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.Base64
import java.util.UUID

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class MdcFilter(
    private val objectMapper: ObjectMapper,
    @Value("\${spring.application.name:monolith}") private val serviceName: String
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val traceId = request.getHeader(TRACE_ID_HEADER) ?: generateTraceId()
        try {
            MDC.put(TRACE_ID_KEY, traceId)
            MDC.put(SERVICE_KEY, serviceName)
            extractUserId(request)?.let { MDC.put(USER_ID_KEY, it) }
            response.setHeader(TRACE_ID_HEADER, traceId)
            filterChain.doFilter(request, response)
        } finally {
            MDC.clear()
        }
    }

    private fun extractUserId(request: HttpServletRequest): String? {
        val authHeader = request.getHeader("Authorization") ?: return null
        if (!authHeader.startsWith("Bearer ")) return null
        return try {
            val token = authHeader.substring(7)
            val payload = token.split(".")[1]
            val decoded = Base64.getUrlDecoder().decode(payload)
            val claims = objectMapper.readTree(decoded)
            claims.get("sub")?.asText()
        } catch (_: Exception) {
            null
        }
    }

    private fun generateTraceId(): String = UUID.randomUUID().toString().replace("-", "").take(16)

    companion object {
        const val TRACE_ID_KEY = "traceId"
        const val TRACE_ID_HEADER = "X-Trace-Id"
        const val USER_ID_KEY = "userId"
        const val SERVICE_KEY = "service"
    }
}
