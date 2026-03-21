package com.hoppingmall.payment.payment.service.strategy

import com.fasterxml.jackson.databind.JsonNode

interface CompensationEventHandler {
    fun supports(eventType: String): Boolean
    fun handle(node: JsonNode)
}
