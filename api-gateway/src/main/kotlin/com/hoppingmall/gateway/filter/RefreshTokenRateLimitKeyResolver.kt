package com.hoppingmall.gateway.filter

import com.fasterxml.jackson.databind.ObjectMapper
import io.jsonwebtoken.Claims
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class RefreshTokenRateLimitKeyResolver(
    @Value("\${jwt.secret}") secret: String
) {

    private val parser = Jwts.parserBuilder()
        .setSigningKey(Keys.hmacShaKeyFor(secret.toByteArray()))
        .build()

    fun userId(body: ByteArray): String? {
        val token = refreshToken(body) ?: return null
        val claims = signedClaims(token) ?: return null
        if (claims[TOKEN_TYPE_CLAIM] != REFRESH_TOKEN_TYPE) return null
        return claims.subject?.takeIf { it.isNotBlank() }
    }

    private fun refreshToken(body: ByteArray): String? {
        if (body.isEmpty()) return null
        val node = try {
            objectMapper.readTree(body)
        } catch (e: Exception) {
            return null
        } ?: return null
        return node.path(REFRESH_TOKEN_FIELD).textValue()?.takeIf { it.isNotBlank() }
    }

    private fun signedClaims(token: String): Claims? =
        try {
            parser.parseClaimsJws(token).body
        } catch (e: ExpiredJwtException) {
            e.claims
        } catch (e: JwtException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }

    companion object {
        private val objectMapper = ObjectMapper()
        private const val REFRESH_TOKEN_FIELD = "refresh_token"
        private const val TOKEN_TYPE_CLAIM = "typ"
        private const val REFRESH_TOKEN_TYPE = "refresh"
    }
}
