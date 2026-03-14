package com.hoppingmall.user.common.vo

import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component

@Component
class PasswordCreator(
    private val passwordEncoder: PasswordEncoder,
    private val passwordPolicy: PasswordPolicy
) {
    fun encode(raw: String): Password {
        passwordPolicy.validate(raw)
        return Password(passwordEncoder.encode(raw))
    }
}
