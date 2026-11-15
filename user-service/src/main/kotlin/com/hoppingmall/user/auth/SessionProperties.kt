package com.hoppingmall.user.auth

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "auth.session")
class SessionProperties(
    val maxSessionsPerUser: Int = 5,
    val reuseGrace: Duration = Duration.ofSeconds(10)
) {
    init {
        require(maxSessionsPerUser > 0) { "auth.session.max-sessions-per-user는 1 이상이어야 합니다: $maxSessionsPerUser" }
        require(!reuseGrace.isNegative) { "auth.session.reuse-grace는 0 이상이어야 합니다: $reuseGrace" }
    }
}
