package com.hoppingmall.mall.global.vo.password.service

import com.hoppingmall.mall.global.vo.password.Password
import com.hoppingmall.mall.global.vo.password.policy.PasswordPolicy
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