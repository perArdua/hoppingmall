package com.hoppingmall.mall.global.jwt

import com.hoppingmall.mall.global.enums.Role
import org.junit.jupiter.api.*
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull

@DisplayName("TokenProvider")
@DisplayNameGeneration(ReplaceUnderscores::class)
class TokenProviderTest {

    private val tokenProvider = TokenProviderImpl(JwtProperties().apply {
        secret = "secret-key".repeat(10)
        accessExpirationMs = 3600L
        refreshExpirationMs = 86400000L
    })

    @Nested
    @DisplayName("generateAccessToken")
    inner class GenerateAccessToken {
        @Test
        fun accessToken을_생성하면_유효한_토큰이_반환된다() {
            val userId = 1L
            val role = Role.SELLER

            val token = tokenProvider.generateAccessToken(userId, role)

            assertNotNull(token)
            assertEquals(userId, tokenProvider.parseAccessToken(token))
        }
    }

    @Nested
    @DisplayName("generateRefreshToken")
    inner class GenerateRefreshToken {
        @Test
        fun refreshToken을_생성하면_유효한_토큰이_반환된다() {
            val userId = 2L

            val token = tokenProvider.generateRefreshToken(userId)

            assertNotNull(token)
            assertEquals(userId, tokenProvider.parseRefreshToken(token))
        }
    }
}
