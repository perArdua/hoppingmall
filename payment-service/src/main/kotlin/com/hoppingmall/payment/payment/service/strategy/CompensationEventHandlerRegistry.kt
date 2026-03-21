package com.hoppingmall.payment.payment.service.strategy

import org.springframework.stereotype.Component

@Component
class CompensationEventHandlerRegistry(
    handlers: List<CompensationEventHandler>
) {
    private val handlerList: List<CompensationEventHandler> = handlers

    fun getHandler(eventType: String): CompensationEventHandler? {
        return handlerList.find { it.supports(eventType) }
    }
}
