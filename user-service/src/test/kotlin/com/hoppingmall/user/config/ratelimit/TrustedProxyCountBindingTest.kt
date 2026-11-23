package com.hoppingmall.user.config.ratelimit

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.springframework.boot.context.properties.source.ConfigurationPropertySources
import org.springframework.core.env.StandardEnvironment
import org.springframework.core.env.SystemEnvironmentPropertySource
import org.springframework.mock.web.MockHttpServletRequest

@DisplayName("믿는 프록시 단수 설정")
@DisplayNameGeneration(ReplaceUnderscores::class)
class TrustedProxyCountBindingTest {

    @Test
    fun 배포_환경_변수_USER_RATELIMIT_TRUSTEDPROXYCOUNT가_단수가_된다() {
        assertThat(resolvedCount(mapOf("USER_RATELIMIT_TRUSTEDPROXYCOUNT" to "1"))).isEqualTo("1")
    }

    @Test
    fun 환경_변수가_없으면_단수는_0이다() {
        assertThat(resolvedCount(emptyMap())).isEqualTo("0")
    }

    @Test
    fun 단수가_0이면_로그인_실패_잠금의_IP도_헤더를_보지_않고_연결_주소다() {
        val request = MockHttpServletRequest("POST", "/api/v1/users/login").apply {
            remoteAddr = "172.18.0.9"
            addHeader("X-Forwarded-For", "198.51.100.7")
        }

        assertThat(ClientIpResolver(0).resolve(request)).isEqualTo("172.18.0.9")
        assertThat(ClientIpResolver(1).resolve(request)).isEqualTo("198.51.100.7")
    }

    private fun resolvedCount(env: Map<String, Any>): String? {
        val environment = StandardEnvironment()
        environment.propertySources.replace(
            StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
            SystemEnvironmentPropertySource(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, env)
        )
        ConfigurationPropertySources.attach(environment)
        return environment.resolvePlaceholders(PLACEHOLDER)
    }

    companion object {
        private const val PLACEHOLDER = "\${user.rate-limit.trusted-proxy-count:0}"
    }
}
