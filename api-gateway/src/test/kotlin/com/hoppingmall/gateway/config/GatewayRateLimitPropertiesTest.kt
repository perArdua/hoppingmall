package com.hoppingmall.gateway.config

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.springframework.boot.context.properties.bind.BindException
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource

@DisplayName("게이트웨이 레이트리밋 설정값")
@DisplayNameGeneration(ReplaceUnderscores::class)
class GatewayRateLimitPropertiesTest {

    private fun bind(values: Map<String, String>): GatewayRateLimitProperties =
        Binder(MapConfigurationPropertySource(values))
            .bindOrCreate("gateway.rate-limit", GatewayRateLimitProperties::class.java)

    @Test
    fun 설정이_없으면_믿는_프록시_단수는_코드의_기본값_0이다() {
        assertThat(bind(emptyMap()).trustedProxyCount).isZero()
    }

    @Test
    fun 설정값으로_믿는_프록시_단수를_바꿀_수_있다() {
        assertThat(bind(mapOf("gateway.rate-limit.trusted-proxy-count" to "2")).trustedProxyCount).isEqualTo(2)
    }

    @Test
    fun 레이트리밋_켜기_설정과_함께_있어도_믿는_프록시_단수를_읽는다() {
        val properties = bind(
            mapOf("gateway.rate-limit.enabled" to "false", "gateway.rate-limit.trusted-proxy-count" to "1")
        )

        assertThat(properties.trustedProxyCount).isEqualTo(1)
    }

    @Test
    fun 믿는_프록시_단수가_음수면_설정을_거절한다() {
        assertThatThrownBy { bind(mapOf("gateway.rate-limit.trusted-proxy-count" to "-1")) }
            .isInstanceOf(BindException::class.java)
            .rootCause()
            .isInstanceOf(IllegalArgumentException::class.java)
    }
}
