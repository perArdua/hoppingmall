package com.hoppingmall.user.auth

import com.hoppingmall.user.auth.exception.InvalidTokenException
import com.hoppingmall.common.UserPrincipal
import com.hoppingmall.user.common.enums.Role
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import io.jsonwebtoken.security.Keys
import org.springframework.stereotype.Component
import java.util.*

@Component
class TokenProviderImpl(
    private val jwtProperties: JwtProperties
) : TokenProvider {

    private val key = Keys.hmacShaKeyFor(jwtProperties.secret.toByteArray())

    override fun generateAccessToken(userId: Long, role: Role): String {
        val now = Date()
        val expiry = Date(now.time + jwtProperties.accessExpirationMs)
        return Jwts.builder()
            .setSubject(userId.toString())
            .claim(TYPE_CLAIM, ACCESS_TYPE)
            .claim(ROLE_CLAIM, role.name)
            .setIssuedAt(now)
            .setExpiration(expiry)
            .signWith(key, SignatureAlgorithm.HS256)
            .compact()
    }

    override fun generateRefreshToken(userId: Long): String {
        val now = Date()
        val expiry = Date(now.time + jwtProperties.refreshExpirationMs)
        return Jwts.builder()
            .setSubject(userId.toString())
            .claim(TYPE_CLAIM, REFRESH_TYPE)
            .setIssuedAt(now)
            .setExpiration(expiry)
            .signWith(key, SignatureAlgorithm.HS256)
            .compact()
    }

    override fun parseAccessToken(token: String): Long =
        requireType(parseClaims(token), ACCESS_TYPE).subject.toLong()

    override fun parseRefreshToken(token: String): Long =
        requireType(parseClaims(token), REFRESH_TYPE).subject.toLong()

    override fun validateToken(token: String): Boolean {
        val claims = parseClaims(token)

        if (claims.expiration.before(Date())) {
            throw InvalidTokenException()
        }
        return true
    }

    override fun getUserIdFromToken(token: String): Long =
        parseClaims(token).subject.toLong()

    override fun getUserRoleFromToken(token: String): Role =
        Role.valueOf(parseClaims(token)[ROLE_CLAIM] as String)

    override fun getUserPrincipal(token: String): UserPrincipal {
        val userId = getUserIdFromToken(token)
        val role = getUserRoleFromToken(token)
        return UserPrincipal.of(userId, role.name)
    }

    override fun getRemainingExpirationMs(token: String): Long {
        val expiration = parseClaims(token).expiration
        return expiration.time - System.currentTimeMillis()
    }

    fun parseClaims(token: String): Claims {
        return try {
            Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .body
        } catch (ex: Exception) {
            throw InvalidTokenException()
        }
    }

    private fun requireType(claims: Claims, expected: String): Claims {
        if (tokenType(claims) != expected) {
            throw InvalidTokenException()
        }
        return claims
    }

    private fun tokenType(claims: Claims): String? = when {
        claims.containsKey(TYPE_CLAIM) -> claims[TYPE_CLAIM] as? String
        claims.containsKey(ROLE_CLAIM) -> ACCESS_TYPE
        else -> REFRESH_TYPE
    }

    companion object {
        private const val TYPE_CLAIM = "typ"
        private const val ROLE_CLAIM = "role"
        private const val ACCESS_TYPE = "access"
        private const val REFRESH_TYPE = "refresh"
    }
}
