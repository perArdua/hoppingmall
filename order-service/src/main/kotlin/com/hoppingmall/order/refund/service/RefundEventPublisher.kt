package com.hoppingmall.order.refund.service

import com.hoppingmall.order.refund.dto.event.RefundCompletedEvent

interface RefundEventPublisher {
    fun publishRefundCompletedEvent(event: RefundCompletedEvent)
}
