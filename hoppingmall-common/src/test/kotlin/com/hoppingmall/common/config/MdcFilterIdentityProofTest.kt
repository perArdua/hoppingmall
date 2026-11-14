package com.hoppingmall.common.config

import io.micrometer.tracing.Tracer
import jakarta.servlet.FilterChain
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import java.util.UUID

@DisplayName("MdcFilter 게이트웨이 증명 확인")
@DisplayNameGeneration(ReplaceUnderscores::class)
class MdcFilterIdentityProofTest {

    private val secret = UUID.randomUUID().toString()

    private val contextRunner = ApplicationContextRunner()
        .withBean(Tracer::class.java, { Tracer.NOOP })
        .withUserConfiguration(MdcFilter::class.java)

    @Test
    fun 비밀값이_있으면_증명_없이_온_userId_헤더를_MDC에_넣지_않는다() {
        contextRunner.withPropertyValues("gateway.identity.secret=$secret").run { context ->
            val userId = capturedUserId(context.getBean(MdcFilter::class.java)) {}

            assertThat(userId).isNull()
        }
    }

    @Test
    fun 비밀값이_있으면_증명이_틀린_userId_헤더를_MDC에_넣지_않는다() {
        contextRunner.withPropertyValues("gateway.identity.secret=$secret").run { context ->
            val userId = capturedUserId(context.getBean(MdcFilter::class.java)) {
                it.addHeader(GATEWAY_PROOF_HEADER, UUID.randomUUID().toString())
            }

            assertThat(userId).isNull()
        }
    }

    @Test
    fun 비밀값이_있으면_증명이_맞는_userId_헤더를_MDC에_넣는다() {
        contextRunner.withPropertyValues("gateway.identity.secret=$secret").run { context ->
            val userId = capturedUserId(context.getBean(MdcFilter::class.java)) {
                it.addHeader(GATEWAY_PROOF_HEADER, secret)
            }

            assertThat(userId).isEqualTo("42")
        }
    }

    @Test
    fun 비밀값이_없으면_userId_헤더를_그대로_MDC에_넣는다() {
        contextRunner.run { context ->
            val userId = capturedUserId(context.getBean(MdcFilter::class.java)) {}

            assertThat(userId).isEqualTo("42")
        }
    }

    private fun capturedUserId(
        filter: MdcFilter,
        customize: (MockHttpServletRequest) -> Unit
    ): String? {
        MDC.clear()
        val request = MockHttpServletRequest().apply {
            addHeader("X-User-Id", "42")
            customize(this)
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
