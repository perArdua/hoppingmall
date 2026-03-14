package com.hoppingmall.gateway.filter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono

@DisplayName("TracingGlobalFilter")
@DisplayNameGeneration(ReplaceUnderscores::class)
class TracingGlobalFilterTest {

    private val filter = TracingGlobalFilter()

    private fun chainCapturing(captured: MutableList<ServerWebExchange>) =
        org.springframework.cloud.gateway.filter.GatewayFilterChain { exchange ->
            captured.add(exchange)
            Mono.empty()
        }

    @Test
    fun X_Trace_Id_미전송시_16자_traceId를_자동_생성하여_요청_헤더에_포함한다() {
        val request = MockServerHttpRequest.get("/test").build()
        val exchange = MockServerWebExchange.from(request)
        val captured = mutableListOf<ServerWebExchange>()

        filter.filter(exchange, chainCapturing(captured)).block()

        val traceId = captured[0].request.headers.getFirst(TracingGlobalFilter.TRACE_ID_HEADER)
        assertThat(traceId).isNotNull().hasSize(16)
        assertThat(traceId).matches("[a-zA-Z0-9]+")
    }

    @Test
    fun 유효한_X_Trace_Id_전송시_기존_값을_그대로_전파한다() {
        val existingTraceId = "abc123def456"
        val request = MockServerHttpRequest.get("/test")
            .header(TracingGlobalFilter.TRACE_ID_HEADER, existingTraceId)
            .build()
        val exchange = MockServerWebExchange.from(request)
        val captured = mutableListOf<ServerWebExchange>()

        filter.filter(exchange, chainCapturing(captured)).block()

        val traceId = captured[0].request.headers.getFirst(TracingGlobalFilter.TRACE_ID_HEADER)
        assertThat(traceId).isEqualTo(existingTraceId)
    }

    @Test
    fun X_Trace_Id가_65자_이상이면_새_traceId를_생성한다() {
        val longTraceId = "a".repeat(65)
        val request = MockServerHttpRequest.get("/test")
            .header(TracingGlobalFilter.TRACE_ID_HEADER, longTraceId)
            .build()
        val exchange = MockServerWebExchange.from(request)
        val captured = mutableListOf<ServerWebExchange>()

        filter.filter(exchange, chainCapturing(captured)).block()

        val traceId = captured[0].request.headers.getFirst(TracingGlobalFilter.TRACE_ID_HEADER)
        assertThat(traceId).isNotEqualTo(longTraceId)
        assertThat(traceId).isNotNull().hasSize(16)
    }

    @Test
    fun X_Trace_Id에_특수문자가_포함되면_새_traceId를_생성한다() {
        val invalidTraceId = "invalid@trace#id!"
        val request = MockServerHttpRequest.get("/test")
            .header(TracingGlobalFilter.TRACE_ID_HEADER, invalidTraceId)
            .build()
        val exchange = MockServerWebExchange.from(request)
        val captured = mutableListOf<ServerWebExchange>()

        filter.filter(exchange, chainCapturing(captured)).block()

        val traceId = captured[0].request.headers.getFirst(TracingGlobalFilter.TRACE_ID_HEADER)
        assertThat(traceId).isNotEqualTo(invalidTraceId)
        assertThat(traceId).isNotNull().hasSize(16)
    }

    @Test
    fun 응답_헤더에_X_Trace_Id가_포함된다() {
        val existingTraceId = "resp-trace-check"
        val request = MockServerHttpRequest.get("/test")
            .header(TracingGlobalFilter.TRACE_ID_HEADER, existingTraceId)
            .build()
        val exchange = MockServerWebExchange.from(request)
        val captured = mutableListOf<ServerWebExchange>()

        filter.filter(exchange, chainCapturing(captured)).block()

        val responseTraceId = captured[0].response.headers.getFirst(TracingGlobalFilter.TRACE_ID_HEADER)
        assertThat(responseTraceId).isEqualTo(existingTraceId)
    }
}
