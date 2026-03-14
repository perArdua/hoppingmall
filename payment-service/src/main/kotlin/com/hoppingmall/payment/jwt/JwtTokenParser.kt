package com.hoppingmall.payment.jwt

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.Base64

@Component
class JwtTokenParser(
    private val objectMapper: ObjectMapper
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun parseUserId(token: String): Long? {
        return try {
            val claims = decodePayload(token)
            claims["sub"]?.toString()?.toLongOrNull()
        } catch (e: Exception) {
            log.debug("JWT userId 파싱 실패: {}", e.message)
            null
        }
    }

    fun parseRole(token: String): String? {
        return try {
            val claims = decodePayload(token)
            claims["role"]?.toString()
        } catch (e: Exception) {
            log.debug("JWT role 파싱 실패: {}", e.message)
            null
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun decodePayload(token: String): Map<String, Any> {
        val parts = token.split(".")
        if (parts.size != 3) {
            throw IllegalArgumentException("Invalid JWT token format")
        }
        val payload = Base64.getUrlDecoder().decode(parts[1])
        return objectMapper.readValue(payload, Map::class.java) as Map<String, Any>
    }
}
