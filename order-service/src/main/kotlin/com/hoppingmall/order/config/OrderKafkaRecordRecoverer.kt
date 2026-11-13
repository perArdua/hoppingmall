package com.hoppingmall.order.config

import com.hoppingmall.dlq.domain.DeadLetterMessage
import com.hoppingmall.dlq.service.DLQCommandService
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.listener.ConsumerRecordRecoverer
import org.springframework.kafka.support.KafkaUtils

class OrderKafkaRecordRecoverer(
    private val dlqCommandService: DLQCommandService
) : ConsumerRecordRecoverer {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun accept(record: ConsumerRecord<*, *>, exception: Exception) {
        log.error("Kafka 메시지 처리 실패 (DLQ): topic={}, offset={}, error={}",
            record.topic(), record.offset(), exception.message)
        val deadLetterMessage = DeadLetterMessage(
            originalTopic = record.topic(),
            originalPartition = record.partition(),
            originalOffset = record.offset(),
            originalKey = record.key()?.toString(),
            originalValue = record.value()?.toString(),
            exception = exception.message,
            timestamp = System.currentTimeMillis(),
            consumerGroup = KafkaUtils.getConsumerGroupId()
        )
        dlqCommandService.saveDLQMessage(deadLetterMessage)
    }
}
