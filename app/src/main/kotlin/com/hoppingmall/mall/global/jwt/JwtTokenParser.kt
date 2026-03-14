package com.hoppingmall.mall.global.jwt

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Component
import java.util.Base64

@Component
class JwtTokenParser(
    private val objectMapper: ObjectMapper
) {

    fun parseUserId(token: String): Long? {
        return try {
            val payload = token.split(".")[1]
            val decoded = Base64.getUrlDecoder().decode(payload)
            val claims = objectMapper.readValue(decoded, Map::class.java)
            (claims["sub"] as? String)?.toLongOrNull()
        } catch (e: Exception) {
            null
        }
    }

    fun parseRole(token: String): String? {
        return try {
            val payload = token.split(".")[1]
            val decoded = Base64.getUrlDecoder().decode(payload)
            val claims = objectMapper.readValue(decoded, Map::class.java)
            claims["role"] as? String
        } catch (e: Exception) {
            null
        }
    }
}
