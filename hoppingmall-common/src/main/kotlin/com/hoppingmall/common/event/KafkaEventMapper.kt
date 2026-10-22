package com.hoppingmall.common.event

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import org.springframework.stereotype.Component

@Component
class KafkaEventMapper(objectMapper: ObjectMapper) {

    private val mapper: ObjectMapper = objectMapper.copy()
        .setPropertyNamingStrategy(PropertyNamingStrategies.LOWER_CAMEL_CASE)

    fun <T> readValue(message: String, type: Class<T>): T = mapper.readValue(message, type)

    fun <T> treeToValue(node: JsonNode, type: Class<T>): T = mapper.treeToValue(node, type)
}
