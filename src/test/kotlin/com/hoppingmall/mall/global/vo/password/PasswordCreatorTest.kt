package com.hoppingmall.mall.global.vo.password

import com.hoppingmall.mall.global.vo.password.exception.WeakPasswordException
import com.hoppingmall.mall.global.vo.password.policy.PasswordPolicy
import com.hoppingmall.mall.global.vo.password.service.PasswordCreator
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.doNothing
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder

class PasswordCreatorTest {

    private val encoder = BCryptPasswordEncoder()
    private val policy: PasswordPolicy = mock()
    private val creator = PasswordCreator(encoder, policy)

    @Test
    fun `정책에 맞는 비밀번호는 정상적으로 인코딩된다`() {
        val raw = "secure123"
        doNothing().whenever(policy).validate(raw)

        val password = creator.encode(raw)

        assertTrue(encoder.matches(raw, password.value))
    }

    @Test
    fun `정책에 어긋나는 비밀번호는 WeakPasswordException 발생`() {
        val raw = "123"
        doThrow(WeakPasswordException()).whenever(policy).validate(raw)

        assertThrows(WeakPasswordException::class.java) {
            creator.encode(raw)
        }
    }
}
