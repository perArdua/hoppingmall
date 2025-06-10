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
    fun `비밀번호 해시화 후 비교 성공`() {
        val raw = "securePass123"
        val hashed = Password.encode(raw)
        assertTrue(Password.matches(raw, hashed))
    }

    @Test
    fun `비밀번호 해시화 후 비교 실패`() {
        val hashed = Password.encode("realPass123")
        assertFalse(Password.matches("wrongPass", hashed))
    }
}
