package com.hoppingmall.notification.consumer

import com.hoppingmall.common.KafkaTopics
import com.hoppingmall.dlq.reprocess.DLQMessageReprocessor
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.stereotype.Component

@Component
class NotificationDLQReprocessor(
    private val notificationEventConsumer: NotificationEventConsumer
) : DLQMessageReprocessor {

    override val topic: String = KafkaTopics.NOTIFICATION

    override val consumerGroup: String = NotificationEventConsumer.CONSUMER_GROUP

    override fun reprocess(key: String?, value: String?) {
        val body = requireNotNull(value) { "DLQ 메시지 본문 없음: topic=$topic" }
        notificationEventConsumer.handleNotificationEvent(
            ConsumerRecord<String, Any>(topic, DLQ_PARTITION, DLQ_OFFSET, key, body)
        )
    }

    companion object {
        private const val DLQ_PARTITION = -1
        private const val DLQ_OFFSET = -1L
    }
}
