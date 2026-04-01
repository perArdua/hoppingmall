package com.hoppingmall.dlq.publisher

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.stereotype.Component

@Component
@ConditionalOnMissingBean(KafkaDLQMessagePublisher::class)
class NoOpDLQMessagePublisher : DLQMessagePublisher {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun publish(topic: String, key: String, value: Any?): Boolean {
        log.warn("KafkaTemplate 미설정으로 DLQ 메시지 재발행 불가: topic={}, key={}", topic, key)
        return false
    }
}
