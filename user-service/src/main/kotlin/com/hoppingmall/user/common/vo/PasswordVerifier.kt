package com.hoppingmall.user.common.vo

import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class PasswordVerifier(
    private val passwordEncoder: PasswordEncoder
) {
    private val dummyPassword: String by lazy { passwordEncoder.encode(UUID.randomUUID().toString()) }

    fun matches(raw: String, encoded: Password): Boolean =
        passwordEncoder.matches(raw, encoded.value)

    fun matchesStored(raw: String, encoded: Password?): Boolean {
        if (encoded == null) {
            passwordEncoder.matches(raw, dummyPassword)
            return false
        }
        return matches(raw, encoded)
    }
}
