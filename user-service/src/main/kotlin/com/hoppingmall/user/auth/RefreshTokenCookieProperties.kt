package com.hoppingmall.user.auth

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "auth.refresh-cookie")
class RefreshTokenCookieProperties(
    allowedOrigins: List<String> = listOf("http://localhost:3000")
) {
    val allowedOrigins: Set<String> = allowedOrigins.map { it.trim().trimEnd('/').lowercase() }
        .filter { it.isNotEmpty() }
        .toSet()

    init {
        require(this.allowedOrigins.isNotEmpty()) { "auth.refresh-cookie.allowed-origins는 하나 이상이어야 합니다" }
        require(this.allowedOrigins.none { it.contains('*') }) {
            "auth.refresh-cookie.allowed-origins에는 와일드카드를 쓸 수 없습니다: ${this.allowedOrigins}"
        }
    }
}
