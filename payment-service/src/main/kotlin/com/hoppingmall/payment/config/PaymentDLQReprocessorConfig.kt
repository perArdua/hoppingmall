package com.hoppingmall.payment.config

import com.hoppingmall.common.KafkaTopics
import com.hoppingmall.dlq.reprocess.DLQMessageReprocessor
import com.hoppingmall.payment.payment.service.PaymentCompensationConsumer
import com.hoppingmall.payment.payment.service.PaymentEventConsumer
import com.hoppingmall.payment.point.service.PointEventConsumer
import com.hoppingmall.payment.refund.service.RefundCompletionConsumer
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class PaymentDLQReprocessorConfig {

    @Bean
    fun paymentEventDLQReprocessor(
        consumer: PaymentEventConsumer,
        @Value("\${kafka.topics.payment:payment}") paymentTopic: String
    ): DLQMessageReprocessor =
        ListenerDLQReprocessor(paymentTopic, PaymentConsumerGroups.PAYMENT_EVENT, consumer::handlePaymentEvent)

    @Bean
    fun paymentCompensationDLQReprocessor(consumer: PaymentCompensationConsumer): DLQMessageReprocessor =
        ListenerDLQReprocessor(
            KafkaTopics.PAYMENT_COMPENSATION,
            PaymentConsumerGroups.PAYMENT_COMPENSATION,
            consumer::handleCompensationEvent
        )

    @Bean
    fun paymentReversalDLQReprocessor(consumer: PaymentCompensationConsumer): DLQMessageReprocessor =
        ListenerDLQReprocessor(
            KafkaTopics.PAYMENT_REVERSAL,
            PaymentConsumerGroups.PAYMENT_COMPENSATION,
            consumer::handlePaymentReversal
        )

    @Bean
    fun pointEarnDLQReprocessor(consumer: PointEventConsumer): DLQMessageReprocessor =
        ListenerDLQReprocessor(
            KafkaTopics.POINT_EARN_REQUEST,
            PaymentConsumerGroups.POINT_EARN,
            consumer::handlePointEarnRequest
        )

    @Bean
    fun refundCompletionDLQReprocessor(consumer: RefundCompletionConsumer): DLQMessageReprocessor =
        ListenerDLQReprocessor(
            KafkaTopics.REFUND_COMPLETION,
            PaymentConsumerGroups.REFUND_COMPLETION,
            consumer::handleRefundCompletionEvent
        )
}

class ListenerDLQReprocessor(
    override val topic: String,
    override val consumerGroup: String,
    private val listener: (String) -> Unit
) : DLQMessageReprocessor {

    override fun reprocess(key: String?, value: String?) {
        listener(requireNotNull(value) { "DLQ 메시지 본문 없음: topic=$topic, group=$consumerGroup" })
    }
}
