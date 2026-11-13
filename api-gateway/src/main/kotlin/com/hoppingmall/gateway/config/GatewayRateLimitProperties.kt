package com.hoppingmall.gateway.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "gateway.rate-limit")
data class GatewayRateLimitProperties(
    val trustedProxyCount: Int = 0
) {
    init {
        require(trustedProxyCount >= 0) { "gateway.rate-limit.trusted-proxy-count는 0 이상이어야 합니다: $trustedProxyCount" }
    }
}
