package com.hoppingmall.mall.global.jwt

import com.hoppingmall.mall.global.enums.Role
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import io.jsonwebtoken.security.Keys
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.*

class TokenProviderTest {

    private lateinit var tokenProvider: TokenProviderImpl
    private val secret = "verysecretkeyverysecretkeyverysecretkey12"
    private val expirationMs = 1000L * 60 * 60

    @BeforeEach
    fun setUp() {
        tokenProvider = TokenProviderImpl(secret, expirationMs)
    }

    @Test
    fun `JWT 생성 후 유효성 검증과 정보 추출이 정상 동작해야 한다`() {
        val token = tokenProvider.generateToken(42L, Role.SELLER)
        val claims = tokenProvider.parseClaims(token)

        assertTrue(tokenProvider.validateToken(token))
        assertEquals(42L, tokenProvider.getUserIdFromToken(token))
        assertEquals(Role.SELLER, tokenProvider.getUserRoleFromToken(token))
        assertEquals("SELLER", claims["role"])
    }

    @Test
    fun `만료된 토큰은 유효하지 않다고 판단해야 한다`() {
        val expiredToken = Jwts.builder()
            .setSubject("99")
            .claim("role", "BUYER")
            .setIssuedAt(Date(System.currentTimeMillis() - 20000))
            .setExpiration(Date(System.currentTimeMillis() - 10000))
            .signWith(Keys.hmacShaKeyFor(secret.toByteArray()), SignatureAlgorithm.HS256)
            .compact()

        assertFalse(tokenProvider.validateToken(expiredToken))
    }

    @Test
    fun `서명이 잘못된 토큰은 유효하지 않다고 판단해야 한다`() {
        val fakeSecret = "differentkeydifferentkeydifferentkey!!"
        val invalidToken = Jwts.builder()
            .setSubject("100")
            .claim("role", "ADMIN")
            .setIssuedAt(Date())
            .setExpiration(Date(System.currentTimeMillis() + 100000))
            .signWith(Keys.hmacShaKeyFor(fakeSecret.toByteArray()), SignatureAlgorithm.HS256)
            .compact()

        assertFalse(tokenProvider.validateToken(invalidToken))
    }

    @Test
    fun `형식이 잘못된 토큰은 예외가 발생하며 false를 반환한다`() {
        val malformedToken = "this.is.not.jwt"
        assertFalse(tokenProvider.validateToken(malformedToken))
    }

    @Test
    fun `지원되지 않는 알고리즘을 가진 토큰은 유효하지 않다`() {
        val invalidToken = "eyJhbGciOiJub25lIn0.eyJzdWIiOiIxMjM0NTYifQ."
        assertFalse(tokenProvider.validateToken(invalidToken))
    }
}
