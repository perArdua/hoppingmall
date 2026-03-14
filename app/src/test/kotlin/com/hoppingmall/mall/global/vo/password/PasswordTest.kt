package com.hoppingmall.mall.global.vo.password

import com.hoppingmall.mall.global.vo.password.exception.WeakPasswordException
import com.hoppingmall.mall.global.vo.password.policy.DefaultPasswordPolicy
import com.hoppingmall.mall.global.vo.password.policy.PasswordPolicy
import com.hoppingmall.mall.global.vo.password.strategy.PasswordMaskingStrategy
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class PasswordTest {

    @Test
    fun `비밀번호 마스킹 전략이 적용된 문자열을 반환한다`() {
        // given
        Password.maskingStrategy = PasswordMaskingStrategy { "***MASKED***" }
        val password = Password("secret")

        // expect
        Assertions.assertEquals("***MASKED***", password.toString())
    }

    @Test
    fun `정책에 따라 8자 이상 비밀번호는 성공`() {
        val policy: PasswordPolicy = DefaultPasswordPolicy()
        Assertions.assertDoesNotThrow {
            policy.validate("12345678")
        }
    }

    @Test
    fun `정책에 따라 8자 미만 비밀번호는 예외 발생`() {
        val policy: PasswordPolicy = DefaultPasswordPolicy()
        Assertions.assertThrows(WeakPasswordException::class.java) {
            policy.validate("1234")
        }
    }
}