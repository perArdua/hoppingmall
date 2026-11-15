package com.hoppingmall.gateway.filter

import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono

enum class GatewayErrorResponse(
    val status: HttpStatus,
    val code: String,
    val message: String
) {
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "A001", "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "A002", "접근 권한이 없습니다."),
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "REQ005", "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요."),
    PAYLOAD_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "G002", "요청 본문이 너무 큽니다.");

    private val body: ByteArray = """{"code":"$code","message":"$message","data":null}""".toByteArray(Charsets.UTF_8)

    fun write(exchange: ServerWebExchange): Mono<Void> {
        val response = exchange.response
        response.statusCode = status
        response.headers.contentType = MediaType.APPLICATION_JSON
        response.headers.contentLength = body.size.toLong()
        return response.writeWith(Mono.fromSupplier { response.bufferFactory().wrap(body) })
    }
}
