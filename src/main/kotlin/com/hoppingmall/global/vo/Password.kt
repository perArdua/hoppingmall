package com.hoppingmall.global.vo

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder

@JvmInline
value class Password private constructor(val value: String) {

    companion object {
        private val encoder = BCryptPasswordEncoder()

        fun encode(raw: String): Password {
            require(raw.length >= 8) { "비밀번호는 최소 8자 이상이어야 합니다." }
            return Password(encoder.encode(raw))
        }

        fun matches(raw: String, hashed: Password): Boolean {
            return encoder.matches(raw, hashed.value)
        }
    }
}
