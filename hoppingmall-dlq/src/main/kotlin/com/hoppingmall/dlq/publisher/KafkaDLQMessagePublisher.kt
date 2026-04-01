package com.hoppingmall.dlq.publisher

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component

@Component
@ConditionalOnBean(KafkaTemplate::class)
class KafkaDLQMessagePublisher(
    private val kafkaTemplate: KafkaTemplate<String, Any>
) : DLQMessagePublisher {

    override fun publish(topic: String, key: String, value: Any?): Boolean {
        kafkaTemplate.send(topic, key, value)
        return true
    }
}
