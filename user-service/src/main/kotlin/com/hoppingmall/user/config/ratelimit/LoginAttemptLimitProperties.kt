package com.hoppingmall.user.config.ratelimit

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "user.login-attempt")
class LoginAttemptLimitProperties(
    val maxFailuresPerEmailAndIp: Int = 10,
    val maxFailuresPerEmail: Int = 50,
    val window: Duration = Duration.ofMinutes(15),
    val blockDuration: Duration = Duration.ofMinutes(15)
) {
    init {
        require(maxFailuresPerEmailAndIp > 0) { "user.login-attempt.max-failures-per-email-and-ip는 1 이상이어야 합니다: $maxFailuresPerEmailAndIp" }
        require(maxFailuresPerEmail > 0) { "user.login-attempt.max-failures-per-email은 1 이상이어야 합니다: $maxFailuresPerEmail" }
        require(window.toMillis() > 0) { "user.login-attempt.window는 0보다 커야 합니다: $window" }
        require(blockDuration.toMillis() > 0) { "user.login-attempt.block-duration은 0보다 커야 합니다: $blockDuration" }
    }
}
