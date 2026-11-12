package com.hoppingmall.payment.config

import com.hoppingmall.common.KafkaTopics
import com.hoppingmall.dlq.domain.DeadLetterMessage
import com.hoppingmall.dlq.service.DLQCommandService
import com.hoppingmall.payment.payment.service.PaymentCompensationConsumer
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.listener.ConsumerRecordRecoverer

class PaymentKafkaRecordRecoverer(
    private val dlqCommandService: DLQCommandService,
    private val paymentCompensationConsumer: PaymentCompensationConsumer
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
            timestamp = System.currentTimeMillis()
        )
        dlqCommandService.saveDLQMessage(deadLetterMessage)
        if (record.topic() == KafkaTopics.PAYMENT_COMPENSATION) {
            record.value()?.toString()?.let { paymentCompensationConsumer.handleRetriesExhausted(it) }
        }
    }
}
