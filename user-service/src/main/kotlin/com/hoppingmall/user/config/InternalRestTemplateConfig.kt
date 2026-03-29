package com.hoppingmall.user.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.client.RestTemplateCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpRequest
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.http.client.ClientHttpResponse
import org.slf4j.MDC

@Configuration
class InternalRestTemplateConfig(
    @Value("\${internal.service.token}") private val serviceToken: String
) {

    @Bean
    fun internalTokenRestTemplateCustomizer(): RestTemplateCustomizer {
        return RestTemplateCustomizer { restTemplate ->
            val interceptors = restTemplate.interceptors.toMutableList()
            interceptors.add(InternalTokenInterceptor(serviceToken))
            restTemplate.interceptors = interceptors
        }
    }

    private class InternalTokenInterceptor(
        private val token: String
    ) : ClientHttpRequestInterceptor {
        override fun intercept(
            request: HttpRequest,
            body: ByteArray,
            execution: ClientHttpRequestExecution
        ): ClientHttpResponse {
            MDC.get("traceId")?.let { request.headers.set("X-Trace-Id", it) }
            MDC.get("globalTraceId")?.let { request.headers.set("X-Global-Trace-Id", it) }
            MDC.get("userId")?.let { request.headers.set("X-User-Id", it) }
            if (request.uri.path.startsWith("/internal/")) {
                request.headers.set("X-Internal-Token", token)
            }
            return execution.execute(request, body)
        }
    }
}
