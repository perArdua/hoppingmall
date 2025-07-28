package com.hoppingmall.mall.global.auth.domain

import org.junit.jupiter.api.*
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Assertions.assertEquals

@DisplayName("RefreshToken")
@DisplayNameGeneration(ReplaceUnderscores::class)
class RefreshTokenTest {

    @Nested
    @DisplayName("생성")
    inner class Creation {
        @Test
        fun RefreshToken_생성_시_입력된_값이_정상적으로_매핑된다() {
            val userId = 1L
            val token = "refresh-token"
            val ttl = 3600L

            val refreshToken = RefreshToken(userId, token, ttl)

            assertEquals(userId, refreshToken.userId)
            assertEquals(token, refreshToken.token)
            assertEquals(ttl, refreshToken.ttl)
        }
    }
}
