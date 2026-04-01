package com.hoppingmall.dlq.domain

enum class DLQStatus {
    PENDING,
    RETRYING,
    PROCESSED,
    FAILED
}
