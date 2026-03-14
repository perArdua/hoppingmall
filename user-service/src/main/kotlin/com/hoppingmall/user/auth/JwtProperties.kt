package com.hoppingmall.user.auth

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "jwt")
class JwtProperties {
    lateinit var secret: String
    var accessExpirationMs: Long = 0
    var refreshExpirationMs: Long = 0
}
