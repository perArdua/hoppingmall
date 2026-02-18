package com.hoppingmall.mall.refund.service

import com.hoppingmall.mall.refund.dto.event.RefundCompletedEvent

interface RefundEventPublisher {
    fun publishRefundCompletedEvent(event: RefundCompletedEvent)
}
