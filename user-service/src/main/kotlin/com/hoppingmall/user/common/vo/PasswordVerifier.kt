package com.hoppingmall.user.common.vo

import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component

@Component
class PasswordVerifier(
    private val passwordEncoder: PasswordEncoder
) {
    fun matches(raw: String, encoded: Password): Boolean {
        return passwordEncoder.matches(raw, encoded.value)
    }

    fun assertMatches(raw: String, encoded: Password) {
        if (!matches(raw, encoded)) {
            throw PasswordNotMatchedException()
        }
    }
}
