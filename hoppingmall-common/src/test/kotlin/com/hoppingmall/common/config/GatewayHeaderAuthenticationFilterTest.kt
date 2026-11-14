package com.hoppingmall.common.config

import com.hoppingmall.common.UserPrincipal
import jakarta.servlet.FilterChain
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import java.util.UUID

@DisplayName("게이트웨이 신원 헤더 인증 필터")
@DisplayNameGeneration(ReplaceUnderscores::class)
class GatewayHeaderAuthenticationFilterTest {

    private val secret = UUID.randomUUID().toString()

    private val contextRunner = ApplicationContextRunner()
        .withUserConfiguration(GatewayHeaderAuthenticationFilter::class.java)

    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun 비밀값이_있으면_증명_없이_온_ADMIN_신원_헤더로는_인증하지_않는다() {
        contextRunner.withPropertyValues("gateway.identity.secret=$secret").run { context ->
            val authentication = authenticate(context.getBean(GatewayHeaderAuthenticationFilter::class.java)) {}

            assertThat(authentication).isNull()
        }
    }

    @Test
    fun 비밀값이_있으면_증명이_틀린_신원_헤더로는_인증하지_않는다() {
        contextRunner.withPropertyValues("gateway.identity.secret=$secret").run { context ->
            val authentication = authenticate(context.getBean(GatewayHeaderAuthenticationFilter::class.java)) {
                it.addHeader(GATEWAY_PROOF_HEADER, UUID.randomUUID().toString())
            }

            assertThat(authentication).isNull()
        }
    }

    @Test
    fun 비밀값이_있으면_증명이_맞는_신원_헤더로_인증한다() {
        contextRunner.withPropertyValues("gateway.identity.secret=$secret").run { context ->
            val authentication = authenticate(context.getBean(GatewayHeaderAuthenticationFilter::class.java)) {
                it.addHeader(GATEWAY_PROOF_HEADER, secret)
            }

            assertThat((authentication!!.principal as UserPrincipal).getUserId()).isEqualTo(7L)
            assertThat(authentication.authorities.map { it.authority }).containsExactly("ROLE_ADMIN")
        }
    }

    @Test
    fun 비밀값이_없고_운영_프로필이_아니면_신원_헤더로_인증한다() {
        contextRunner.run { context ->
            val authentication = authenticate(context.getBean(GatewayHeaderAuthenticationFilter::class.java)) {}

            assertThat((authentication!!.principal as UserPrincipal).getUserId()).isEqualTo(7L)
        }
    }

    @Test
    fun docker_프로필에서_비밀값이_없으면_기동하지_않는다() {
        contextRunner.withPropertyValues("spring.profiles.active=docker,grpc").run { context ->
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
    fun docker_프로필에서는_비밀값이_있으면_기동하고_증명_없는_신원_헤더를_무시한다() {
        contextRunner.withPropertyValues("spring.profiles.active=docker", "gateway.identity.secret=$secret").run { context ->
            assertThat(context).hasNotFailed()

            val authentication = authenticate(context.getBean(GatewayHeaderAuthenticationFilter::class.java)) {}

            assertThat(authentication).isNull()
        }
    }

    private fun authenticate(
        filter: GatewayHeaderAuthenticationFilter,
        customize: (MockHttpServletRequest) -> Unit
    ): Authentication? {
        val request = MockHttpServletRequest("GET", "/api/v1/admin/sellers").apply {
            addHeader("X-User-Id", "7")
            addHeader("x-user-role", "ADMIN")
        }
        customize(request)
        var seen: Authentication? = null
        val chain = FilterChain { _, _ -> seen = SecurityContextHolder.getContext().authentication }

        filter.doFilter(request, MockHttpServletResponse(), chain)

        return seen
    }

    companion object {
        private const val GATEWAY_PROOF_HEADER = "x-gateway-identity"
    }
}
