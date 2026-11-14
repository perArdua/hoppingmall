package com.hoppingmall.consistency.auth

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import java.util.Date

object TestTokens {

    const val SECRET = "consistency-monitor-test-secret-key-32bytes!!"
    private const val OTHER_SECRET = "another-secret-key-that-is-not-the-monitor-key!!"

    fun admin(userId: Long = 7L): String = token(userId, "ADMIN")

    fun bearer(token: String): String = "Bearer $token"

    fun token(
        userId: Long,
        role: String?,
        type: String? = "access",
        expiresInMs: Long = 60_000,
        secret: String = SECRET
    ): String {
        val now = System.currentTimeMillis()
        val builder = Jwts.builder()
            .setSubject(userId.toString())
            .setIssuedAt(Date(now))
            .setExpiration(Date(now + expiresInMs))
        type?.let { builder.claim("typ", it) }
        role?.let { builder.claim("role", it) }
        return builder.signWith(Keys.hmacShaKeyFor(secret.toByteArray())).compact()
    }

    fun signedWithOtherKey(): String = token(7L, "ADMIN", secret = OTHER_SECRET)
}
