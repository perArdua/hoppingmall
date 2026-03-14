package com.hoppingmall.mall.global.vo.password

import com.hoppingmall.mall.global.vo.password.exception.PasswordNotMatchedException
import com.hoppingmall.mall.global.vo.password.service.PasswordVerifier
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder

class PasswordVerifierTest {

    private val encoder = BCryptPasswordEncoder()
    private val verifier = PasswordVerifier(encoder)

    @Test
    fun `비밀번호가 일치하면 예외 없이 통과한다`() {
        val raw = "password123"
        val encoded = Password(encoder.encode(raw))

        verifier.assertMatches(raw, encoded)
    }

    @Test
    fun `비밀번호가 일치하지 않으면 PasswordNotMatchedException 발생`() {
        val raw = "password123"
        val encoded = Password(encoder.encode("otherpass"))

        assertThrows(PasswordNotMatchedException::class.java) {
            verifier.assertMatches(raw, encoded)
        }
    }
}
