package com.hoppingmall.gateway.filter

import org.springframework.beans.factory.annotation.Value
import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.cloud.gateway.filter.GlobalFilter
import org.springframework.core.Ordered
import org.springframework.core.env.Environment
import org.springframework.core.env.Profiles
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono

@Component
class IdentityProofGlobalFilter(
    @Value("\${gateway.identity.secret:}") identitySecret: String,
    environment: Environment
) : GlobalFilter, Ordered {

    private val proof: String? = identitySecret.takeIf { it.isNotBlank() }

    init {
        check(proof != null || !environment.acceptsProfiles(ENFORCED_PROFILES)) {
            "docker·k8s 프로필에서는 GATEWAY_IDENTITY_SECRET 환경 변수가 있어야 합니다"
        }
    }

    override fun filter(exchange: ServerWebExchange, chain: GatewayFilterChain): Mono<Void> {
        val headers = exchange.request.headers
        val attachProof = proof != null && headers.containsKey(USER_ID_HEADER)
        if (!attachProof && !headers.containsKey(IDENTITY_PROOF_HEADER)) {
            return chain.filter(exchange)
        }

        val request = exchange.request.mutate()
            .headers { httpHeaders ->
                httpHeaders.remove(IDENTITY_PROOF_HEADER)
                if (attachProof) httpHeaders.set(IDENTITY_PROOF_HEADER, proof)
            }
            .build()
        return chain.filter(exchange.mutate().request(request).build())
    }

    override fun getOrder(): Int = ORDER

    companion object {
        const val IDENTITY_PROOF_HEADER = "x-gateway-identity"
        const val ORDER = Ordered.LOWEST_PRECEDENCE - 100
        private const val USER_ID_HEADER = "x-user-id"
        private val ENFORCED_PROFILES = Profiles.of("docker | k8s")
    }
}
