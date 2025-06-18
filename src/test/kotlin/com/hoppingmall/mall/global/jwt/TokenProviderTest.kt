package com.hoppingmall.mall.global.jwt

import com.hoppingmall.mall.global.auth.exception.InvalidTokenException
import com.hoppingmall.mall.global.enums.Role
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TokenProviderTest {

    private lateinit var tokenProvider: TokenProviderImpl

    @BeforeEach
    fun setUp() {
        val props = JwtProperties().apply {
            secret = "secret-key".repeat(10)
            accessExpirationMs = 3600L
            refreshExpirationMs = 86400000L
        }
        tokenProvider = TokenProviderImpl(props)
    }

    @Test
    fun `액세스 토큰 생성과 파싱이 정상 동작해야 한다`() {
        val token = tokenProvider.generateAccessToken(1L, Role.SELLER)

        assertTrue(tokenProvider.validateToken(token))
        assertEquals(1L, tokenProvider.parseAccessToken(token))
        assertEquals(1L, tokenProvider.getUserIdFromToken(token))
        assertEquals(Role.SELLER, tokenProvider.getUserRoleFromToken(token))
    }

    @Test
    fun `리프레시 토큰 생성과 파싱이 정상 동작해야 한다`() {
        val token = tokenProvider.generateRefreshToken(2L)

        assertTrue(tokenProvider.validateToken(token))
        assertEquals(2L, tokenProvider.parseRefreshToken(token))
    }

    @Test
    fun `유저 정보를 통해 UserPrincipal을 추출할 수 있어야 한다`() {
        val token = tokenProvider.generateAccessToken(3L, Role.BUYER)

        val principal = tokenProvider.getUserPrincipal(token)

        assertEquals(3L, principal.getUserId())
        assertEquals(Role.BUYER.name, principal.authorities.first().authority.removePrefix("ROLE_"))
    }

    @Test
    fun `만료된 토큰은 InvalidTokenException이 발생한다`() {
        val shortLivedProvider = TokenProviderImpl(
            JwtProperties().apply {
                secret = "secret-key".repeat(10)
                accessExpirationMs = 1L
                refreshExpirationMs = 1L
            }
        )
        val token = shortLivedProvider.generateAccessToken(99L, Role.ADMIN)

        Thread.sleep(10)

        assertThrows(InvalidTokenException::class.java) {
            shortLivedProvider.validateToken(token)
        }
    }

    @Test
    fun `형식이 잘못된 토큰은 InvalidTokenException이 발생한다`() {
        val invalidToken = "this.is.not.valid.token"
        assertThrows(InvalidTokenException::class.java) {
            tokenProvider.validateToken(invalidToken)
        }

        assertThrows(InvalidTokenException::class.java) {
            tokenProvider.getUserIdFromToken(invalidToken)
        }

        assertThrows(InvalidTokenException::class.java) {
            tokenProvider.getUserRoleFromToken(invalidToken)
        }
    }
}
