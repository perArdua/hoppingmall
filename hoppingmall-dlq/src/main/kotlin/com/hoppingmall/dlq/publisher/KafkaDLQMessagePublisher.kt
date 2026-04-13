package com.hoppingmall.dlq.publisher

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
@ConditionalOnBean(KafkaTemplate::class)
class KafkaDLQMessagePublisher(
    private val kafkaTemplate: KafkaTemplate<String, Any>
) : DLQMessagePublisher {

    private val logger = LoggerFactory.getLogger(KafkaDLQMessagePublisher::class.java)

    override fun publish(topic: String, key: String, value: Any?): Boolean {
        return try {
            kafkaTemplate.send(topic, key, value).get(5, TimeUnit.SECONDS)
            true
        } catch (e: Exception) {
            logger.error("DLQ 메시지 발행 실패: topic=$topic, key=$key", e)
            false
        }
    }
}
