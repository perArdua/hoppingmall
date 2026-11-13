package com.hoppingmall.dlq.publisher

import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import java.util.concurrent.TimeUnit

class KafkaDLQMessagePublisher(
    private val kafkaTemplate: KafkaTemplate<String, Any>
) : DLQMessagePublisher {

    private val logger = LoggerFactory.getLogger(KafkaDLQMessagePublisher::class.java)

    override fun publish(topic: String, key: String, value: Any?): Boolean {
        return try {
            if (kafkaTemplate.isTransactional) {
                kafkaTemplate.executeInTransaction { operations ->
                    operations.send(topic, key, value).get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                }
            } else {
                kafkaTemplate.send(topic, key, value).get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            }
            true
        } catch (e: Exception) {
            logger.error("DLQ 메시지 발행 실패: topic=$topic, key=$key", e)
            false
        }
    }

    companion object {
        private const val SEND_TIMEOUT_SECONDS = 5L
    }
}
