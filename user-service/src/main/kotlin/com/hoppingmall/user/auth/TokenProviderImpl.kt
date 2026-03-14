package com.hoppingmall.user.auth

import com.hoppingmall.user.auth.exception.InvalidTokenException
import com.hoppingmall.user.common.UserPrincipal
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
            .claim("role", role.name)
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
            .setIssuedAt(now)
            .setExpiration(expiry)
            .signWith(key, SignatureAlgorithm.HS256)
            .compact()
    }

    override fun parseAccessToken(token: String): Long =
        parseClaims(token).subject.toLong()

    override fun parseRefreshToken(token: String): Long =
        parseClaims(token).subject.toLong()

    override fun validateToken(token: String): Boolean {
        val claims = parseClaims(token)

        if (claims.expiration.before(Date())) {
            throw InvalidTokenException()
        }
        return true
    }

    override fun getUserIdFromToken(token: String): Long {
        return parseClaims(token).subject.toLong()
    }

    override fun getUserRoleFromToken(token: String): Role {
        return Role.valueOf(parseClaims(token)["role"] as String)
    }

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
}
