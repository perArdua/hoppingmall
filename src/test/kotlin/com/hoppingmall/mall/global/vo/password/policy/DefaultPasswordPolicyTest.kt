package com.hoppingmall.mall.global.vo.password.policy

import com.hoppingmall.mall.global.vo.password.exception.WeakPasswordException
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class DefaultPasswordPolicyTest {

    private val policy = DefaultPasswordPolicy()

    @Test
    fun `8자 이상의 비밀번호는 유효성 검증을 통과한다`() {
        assertDoesNotThrow {
            policy.validate("password123")
        }
    }

    @Test
    fun `8자 미만의 비밀번호는 WeakPasswordException을 발생시킨다`() {
        assertThrows(WeakPasswordException::class.java) {
            policy.validate("1234567")
        }
    }
}