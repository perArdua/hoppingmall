package com.hoppingmall.common.config

import com.hoppingmall.common.UserPrincipal
import io.micrometer.tracing.Tracer
import jakarta.servlet.FilterChain
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.core.env.StandardEnvironment
import org.springframework.core.env.SystemEnvironmentPropertySource
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import java.util.UUID

@DisplayName("게이트웨이 신원 증명 비밀값 회전")
@DisplayNameGeneration(ReplaceUnderscores::class)
class GatewayIdentityProofRotationTest {

    private val current = UUID.randomUUID().toString()
    private val previous = UUID.randomUUID().toString()

    private val filterRunner = ApplicationContextRunner()
        .withUserConfiguration(GatewayHeaderAuthenticationFilter::class.java)

    private val mdcRunner = ApplicationContextRunner()
        .withBean(Tracer::class.java, { Tracer.NOOP })
        .withUserConfiguration(MdcFilter::class.java)

    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clearContext()
        MDC.clear()
    }

    @Test
    fun 이전_비밀값을_함께_설정하면_이전_값으로_온_증명도_신원_헤더로_인증한다() {
        filterRunner.withPropertyValues(
            "gateway.identity.secret=$current",
            "gateway.identity.previous-secret=$previous"
        ).run { context ->
            val filter = context.getBean(GatewayHeaderAuthenticationFilter::class.java)

            assertThat(authenticatedUserId(filter, previous)).isEqualTo(7L)
            assertThat(authenticatedUserId(filter, current)).isEqualTo(7L)
            assertThat(authenticatedUserId(filter, UUID.randomUUID().toString())).isNull()
        }
    }

    @Test
    fun 이전_비밀값을_빼면_이전_값으로_온_증명은_인증하지_않는다() {
        filterRunner.withPropertyValues("gateway.identity.secret=$current").run { context ->
            val filter = context.getBean(GatewayHeaderAuthenticationFilter::class.java)

            assertThat(authenticatedUserId(filter, previous)).isNull()
            assertThat(authenticatedUserId(filter, current)).isEqualTo(7L)
        }
    }

    @Test
    fun 이전_비밀값은_환경_변수_GATEWAY_IDENTITY_PREVIOUS_SECRET으로도_받는다() {
        filterRunner
            .withInitializer { context ->
                context.environment.propertySources.addFirst(
                    SystemEnvironmentPropertySource(
                        StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                        mapOf<String, Any>(
                            "GATEWAY_IDENTITY_SECRET" to current,
                            "GATEWAY_IDENTITY_PREVIOUS_SECRET" to previous
                        )
                    )
                )
            }
            .run { context ->
                val filter = context.getBean(GatewayHeaderAuthenticationFilter::class.java)

                assertThat(authenticatedUserId(filter, previous)).isEqualTo(7L)
            }
    }

    @Test
    fun docker_프로필에서_이전_비밀값만_있으면_기동하지_않는다() {
        filterRunner.withPropertyValues(
            "spring.profiles.active=docker",
            "gateway.identity.previous-secret=$previous"
        ).run { context ->
            assertThat(context).hasFailed()
            assertThat(context.startupFailure).rootCause().hasMessageContaining("GATEWAY_IDENTITY_SECRET")
        }
    }

    @Test
    fun MdcFilter도_이전_비밀값으로_온_증명의_userId를_MDC에_넣는다() {
        mdcRunner.withPropertyValues(
            "gateway.identity.secret=$current",
            "gateway.identity.previous-secret=$previous"
        ).run { context ->
            val filter = context.getBean(MdcFilter::class.java)

            assertThat(mdcUserId(filter, previous)).isEqualTo("42")
            assertThat(mdcUserId(filter, UUID.randomUUID().toString())).isNull()
        }
    }

    private fun authenticatedUserId(filter: GatewayHeaderAuthenticationFilter, proof: String): Long? {
        SecurityContextHolder.clearContext()
        val request = MockHttpServletRequest("GET", "/api/v1/admin/sellers").apply {
            addHeader("X-User-Id", "7")
            addHeader("x-user-role", "ADMIN")
            addHeader(GATEWAY_PROOF_HEADER, proof)
        }
        var seen: Authentication? = null
        filter.doFilter(request, MockHttpServletResponse(), FilterChain { _, _ ->
            seen = SecurityContextHolder.getContext().authentication
        })
        return (seen?.principal as UserPrincipal?)?.getUserId()
    }

    private fun mdcUserId(filter: MdcFilter, proof: String): String? {
        MDC.clear()
        val request = MockHttpServletRequest().apply {
            addHeader("X-User-Id", "42")
            addHeader(GATEWAY_PROOF_HEADER, proof)
        }
        var userId: String? = null
        filter.doFilter(request, MockHttpServletResponse(), FilterChain { _, _ ->
            userId = MDC.get("userId")
        })
        return userId
    }

    companion object {
        private const val GATEWAY_PROOF_HEADER = "x-gateway-identity"
    }
}
