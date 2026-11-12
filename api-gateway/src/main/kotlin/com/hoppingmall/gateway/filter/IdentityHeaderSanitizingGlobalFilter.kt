package com.hoppingmall.gateway.filter

import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.cloud.gateway.filter.GlobalFilter
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class IdentityHeaderSanitizingGlobalFilter : GlobalFilter {

    override fun filter(exchange: ServerWebExchange, chain: GatewayFilterChain): Mono<Void> {
        val headers = exchange.request.headers
        if (IDENTITY_HEADERS.none { headers.containsKey(it) }) {
            return chain.filter(exchange)
        }

        val sanitizedRequest = exchange.request.mutate()
            .headers { httpHeaders -> IDENTITY_HEADERS.forEach { httpHeaders.remove(it) } }
            .build()
        return chain.filter(exchange.mutate().request(sanitizedRequest).build())
    }

    companion object {
        private val IDENTITY_HEADERS = listOf("x-user-id", "x-user-role")
    }
}
