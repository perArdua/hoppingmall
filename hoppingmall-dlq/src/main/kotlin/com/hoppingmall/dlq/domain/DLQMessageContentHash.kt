package com.hoppingmall.dlq.domain

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import java.security.MessageDigest
import java.util.HexFormat

object DLQMessageContentHash {

    private const val SEPARATOR = "\u0000"

    private val canonicalMapper: ObjectMapper = ObjectMapper()
        .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)

    fun of(topic: String, key: String?, value: String?): String {
        val content = listOf(topic, key.orEmpty(), canonical(value)).joinToString(SEPARATOR)
        val digest = MessageDigest.getInstance("SHA-256").digest(content.toByteArray(Charsets.UTF_8))
        return HexFormat.of().formatHex(digest)
    }

    private fun canonical(value: String?): String {
        if (value == null) return ""
        return runCatching {
            canonicalMapper.writeValueAsString(canonicalMapper.readValue(value, Any::class.java))
        }.getOrDefault(value)
    }
}
