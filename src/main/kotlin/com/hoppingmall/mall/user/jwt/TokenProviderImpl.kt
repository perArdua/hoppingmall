package com.hoppingmall.mall.user.jwt

import com.hoppingmall.mall.global.enums.Role
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
}
