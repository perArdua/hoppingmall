package com.hoppingmall.gateway.filter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import java.util.UUID

@DisplayName("신원 증명 헤더 전역 필터의 기동 조건")
@DisplayNameGeneration(ReplaceUnderscores::class)
class IdentityProofGlobalFilterTest {

    private val secret = UUID.randomUUID().toString()

    private val contextRunner = ApplicationContextRunner()
        .withUserConfiguration(IdentityProofGlobalFilter::class.java)

    @Test
    fun docker_프로필에서_비밀값이_없으면_기동하지_않는다() {
        contextRunner.withPropertyValues("spring.profiles.active=docker").run { context ->
            assertThat(context).hasFailed()
            assertThat(context.startupFailure).rootCause().hasMessageContaining("GATEWAY_IDENTITY_SECRET")
        }
    }

    @Test
    fun k8s_프로필에서_비밀값이_비어_있으면_기동하지_않는다() {
        contextRunner.withPropertyValues("spring.profiles.active=k8s", "gateway.identity.secret=").run { context ->
            assertThat(context).hasFailed()
            assertThat(context.startupFailure).rootCause().hasMessageContaining("GATEWAY_IDENTITY_SECRET")
        }
    }

    @Test
    fun 운영_프로필이_아니고_비밀값이_없으면_신원_헤더가_있어도_증명_헤더를_붙이지_않는다() {
        contextRunner.run { context ->
            val forwarded = forward(context.getBean(IdentityProofGlobalFilter::class.java))

            assertThat(forwarded.request.headers.containsKey(IdentityProofGlobalFilter.IDENTITY_PROOF_HEADER)).isFalse()
        }
    }

    @Test
    fun docker_프로필에서_비밀값이_있으면_신원_헤더가_있는_요청에_증명_헤더를_붙인다() {
        contextRunner.withPropertyValues("spring.profiles.active=docker", "gateway.identity.secret=$secret").run { context ->
            val forwarded = forward(context.getBean(IdentityProofGlobalFilter::class.java))

            assertThat(forwarded.request.headers[IdentityProofGlobalFilter.IDENTITY_PROOF_HEADER]).containsExactly(secret)
        }
    }

    private fun forward(filter: IdentityProofGlobalFilter): ServerWebExchange {
        val exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/v1/orders")
                .header("x-user-id", "7")
                .header(IdentityProofGlobalFilter.IDENTITY_PROOF_HEADER, "forged")
        )
        var forwarded: ServerWebExchange = exchange
        val chain = GatewayFilterChain { next ->
            forwarded = next
            Mono.empty()
        }

        filter.filter(exchange, chain).block()

        return forwarded
    }
}
