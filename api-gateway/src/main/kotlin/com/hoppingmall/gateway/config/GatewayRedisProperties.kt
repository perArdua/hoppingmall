package com.hoppingmall.gateway.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "gateway.redis")
data class GatewayRedisProperties(
    val commandTimeout: Duration = Duration.ofMillis(500)
)
