package com.hoppingmall.order.outbox.domain

enum class OutboxStatus {
    PENDING,
    RETRYING,
    PUBLISHED,
    FAILED
}
