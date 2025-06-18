package com.hoppingmall.mall.global.auth.domain

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class RefreshTokenTest {

    @Test
    fun `RefreshToken은 주어진 값으로 정확하게 생성되어야 한다`() {
        val refreshToken = RefreshToken(
            userId = 1L,
            token = "sample-refresh-token",
            ttl = 3600L
        )

        assertEquals(1L, refreshToken.userId)
        assertEquals("sample-refresh-token", refreshToken.token)
        assertEquals(3600L, refreshToken.ttl)
    }

    @Test
    fun `동일한 값으로 생성된 RefreshToken은 동등성 비교에서 같아야 한다`() {
        val a = RefreshToken(1L, "abc", 1000L)
        val b = RefreshToken(1L, "abc", 1000L)

        assertEquals(a, b) // equals 비교
        assertEquals(a.hashCode(), b.hashCode())
    }
}
