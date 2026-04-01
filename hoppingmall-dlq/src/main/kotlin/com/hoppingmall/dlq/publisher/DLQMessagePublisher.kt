package com.hoppingmall.dlq.publisher

interface DLQMessagePublisher {
    fun publish(topic: String, key: String, value: Any?): Boolean
}
