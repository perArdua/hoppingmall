package com.hoppingmall.global.vo

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PasswordTest {

    @Test
    fun `비밀번호는 8자 미만이면 예외를 던진다`() {
        val exception = assertThrows(IllegalArgumentException::class.java) {
            Password.encode("short")
        }
        assertEquals("비밀번호는 최소 8자 이상이어야 합니다.", exception.message)
    }

    @Test
    fun `비밀번호는 encode와 matches로 비교할 수 있다`() {
        val rawPassword = "secure123"
        val password = Password.encode(rawPassword)

        assertTrue(Password.matches(rawPassword, password))
        assertFalse(Password.matches("wrongpass", password))
    }
}
