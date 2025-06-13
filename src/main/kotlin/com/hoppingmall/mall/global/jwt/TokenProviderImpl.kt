package com.hoppingmall.mall.global.jwt

import com.hoppingmall.mall.global.auth.UserPrincipal
import com.hoppingmall.mall.global.enums.Role
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.*

@Component
class TokenProviderImpl(
    @Value("\${jwt.secret}") private val secret: String,
    @Value("\${jwt.expiration-ms:3600000}") private val expirationMs: Long
) : TokenProvider {

    private val key = Keys.hmacShaKeyFor(secret.toByteArray())

    override fun generateToken(userId: Long, role: Role): String {
        val now = Date()
        val expiry = Date(now.time + expirationMs)

        return Jwts.builder()
            .setSubject(userId.toString())
            .claim("role", role.name)
            .setIssuedAt(now)
            .setExpiration(expiry)
            .signWith(key, SignatureAlgorithm.HS256)
            .compact()
    }

    override fun validateToken(token: String): Boolean {
        return try {
            val claims = parseClaims(token)
            !claims.expiration.before(Date())
        } catch (e: Exception) {
            false
        }
    }

    override fun getUserIdFromToken(token: String): Long {
        val claims = parseClaims(token)
        return claims.subject.toLong()
    }

    override fun getUserRoleFromToken(token: String): Role {
        val claims = parseClaims(token)
        return Role.valueOf(claims["role"] as String)
    }

    override fun getUserPrincipal(token: String): UserPrincipal {
        val userId = getUserIdFromToken(token)
        val role = getUserRoleFromToken(token)
        return UserPrincipal.of(userId, role.name)
    }

    internal fun parseClaims(token: String): Claims {
        return Jwts.parserBuilder()
            .setSigningKey(key)
            .build()
            .parseClaimsJws(token)
            .body
    }
}
