package com.hoppingmall.gateway.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource
import java.time.Duration

@DisplayName("게이트웨이 Redis 설정값")
@DisplayNameGeneration(ReplaceUnderscores::class)
class GatewayRedisPropertiesTest {

    private fun bind(values: Map<String, String>): GatewayRedisProperties =
        Binder(MapConfigurationPropertySource(values))
            .bindOrCreate("gateway.redis", GatewayRedisProperties::class.java)

    @Test
    fun 설정이_없으면_명령_시간_제한은_코드의_기본값_500ms다() {
        assertThat(bind(emptyMap()).commandTimeout).isEqualTo(Duration.ofMillis(500))
    }

    @Test
    fun 설정값으로_명령_시간_제한을_바꿀_수_있다() {
        assertThat(bind(mapOf("gateway.redis.command-timeout" to "200ms")).commandTimeout)
            .isEqualTo(Duration.ofMillis(200))
    }
}
