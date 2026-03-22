package com.hoppingmall.gateway.filter

import io.micrometer.tracing.Tracer
import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.cloud.gateway.filter.GlobalFilter
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import java.util.UUID

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
class TracingGlobalFilter(
    private val tracer: Tracer
) : GlobalFilter {
    override fun filter(exchange: ServerWebExchange, chain: GatewayFilterChain): Mono<Void> {
        val otelTraceId = tracer.currentSpan()?.context()?.traceId()
        val incomingTraceId = exchange.request.headers.getFirst(TRACE_ID_HEADER)
        val traceId = otelTraceId
            ?: if (isValidTraceId(incomingTraceId)) incomingTraceId!! else generateTraceId()

        val modifiedRequest = exchange.request.mutate()
            .header(TRACE_ID_HEADER, traceId)
            .build()
        val modifiedExchange = exchange.mutate().request(modifiedRequest).build()
        modifiedExchange.response.headers.set(TRACE_ID_HEADER, traceId)
        return chain.filter(modifiedExchange)
    }

    private fun isValidTraceId(traceId: String?): Boolean =
        traceId != null && traceId.length in 1..64 && traceId.matches(TRACE_ID_PATTERN)

    private fun generateTraceId(): String = UUID.randomUUID().toString().replace("-", "").take(16)

    companion object {
        const val TRACE_ID_HEADER = "X-Trace-Id"
        private val TRACE_ID_PATTERN = Regex("[a-zA-Z0-9\\-]+")
    }
}
