package com.hoppingmall.mall.global.vo.password

import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component

@Component
class PasswordCreator(
    private val passwordEncoder: PasswordEncoder
) {
    fun encode(raw: String): Password {
        require(raw.length >= 8) { "비밀번호는 최소 8자 이상이어야 합니다." }
        return Password(passwordEncoder.encode(raw))
    }
}

@Component
class PasswordVerifier(
    private val passwordEncoder: PasswordEncoder
) {
    fun matches(raw: String, encoded: Password): Boolean {
        return passwordEncoder.matches(raw, encoded.value)
    }

    fun assertMatches(raw: String, encoded: Password) {
        require(matches(raw, encoded)) { "비밀번호가 일치하지 않습니다." }
    }
}
