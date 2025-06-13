package com.hoppingmall.mall.global.vo.email

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class EmailTest {

    @Test
    fun `유효한 이메일이면 객체 생성에 성공한다`() {
        val email = Email("test@example.com")
        Assertions.assertEquals("test@example.com", email.value)
    }

    @Test
    fun `이메일 형식이 잘못되면 예외가 발생한다`() {
        val exception = Assertions.assertThrows(IllegalArgumentException::class.java) {
            Email("invalid-email")
        }
        Assertions.assertTrue(exception.message!!.startsWith("유효하지 않은 이메일 형식입니다"))
    }

    @Test
    fun `toString은 이메일 값을 반환한다`() {
        val email = Email("hello@world.com")
        Assertions.assertEquals("hello@world.com", email.toString())
    }
}