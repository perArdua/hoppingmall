package com.hoppingmall.user.auth

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.springframework.boot.context.properties.bind.Bindable
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.core.env.StandardEnvironment
import org.springframework.core.env.SystemEnvironmentPropertySource

@DisplayName("리프레시 토큰 쿠키 허용 출처 환경 변수")
@DisplayNameGeneration(ReplaceUnderscores::class)
class RefreshTokenCookiePropertiesBindingTest {

    @Test
    fun AUTH_REFRESHCOOKIE_ALLOWEDORIGINS의_쉼표로_나눈_출처가_허용_목록이_된다() {
        val properties = bind(mapOf("AUTH_REFRESHCOOKIE_ALLOWEDORIGINS" to "https://shop.example.com,https://admin.example.com"))

        assertThat(properties.allowedOrigins).containsExactlyInAnyOrder("https://shop.example.com", "https://admin.example.com")
    }

    @Test
    fun 환경_변수가_없으면_허용_목록은_로컬_프론트_하나다() {
        assertThat(bind(emptyMap()).allowedOrigins).containsExactly("http://localhost:3000")
    }

    private fun bind(env: Map<String, Any>): RefreshTokenCookieProperties {
        val environment = StandardEnvironment()
        environment.propertySources.replace(
            StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
            SystemEnvironmentPropertySource(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, env)
        )
        return Binder.get(environment)
            .bindOrCreate("auth.refresh-cookie", Bindable.of(RefreshTokenCookieProperties::class.java))
    }
}
