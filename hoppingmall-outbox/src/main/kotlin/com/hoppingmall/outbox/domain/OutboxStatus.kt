package com.hoppingmall.outbox.domain

enum class OutboxStatus {
    PENDING,
    RETRYING,
    PUBLISHED,
    FAILED
}
