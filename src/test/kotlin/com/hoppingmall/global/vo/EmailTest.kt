package com.hoppingmall.global.vo

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class EmailTest {

    @Test
    fun `이메일 형식이 유효하면 객체 생성에 성공한다`() {
        val email = Email("test@example.com")
        assertEquals("test@example.com", email.value)
    }

    @Test
    fun `이메일 형식이 유효하지 않으면 예외를 던진다`() {
        val exception = assertThrows(IllegalArgumentException::class.java) {
            Email("invalid-email")
        }
        assertEquals("유효하지 않은 이메일 형식입니다.", exception.message)
    }
}
