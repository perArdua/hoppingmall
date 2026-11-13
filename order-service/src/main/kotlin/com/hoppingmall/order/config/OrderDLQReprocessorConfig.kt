package com.hoppingmall.order.config

import com.hoppingmall.common.KafkaTopics
import com.hoppingmall.dlq.reprocess.DLQMessageReprocessor
import com.hoppingmall.order.order.service.OrderCancellationResultConsumer
import com.hoppingmall.order.order.service.OrderSagaConsumer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OrderDLQReprocessorConfig {

    @Bean
    fun paymentCompletedDLQReprocessor(consumer: OrderSagaConsumer): DLQMessageReprocessor =
        ListenerDLQReprocessor(KafkaTopics.PAYMENT, OrderConsumerGroups.ORDER_SAGA, consumer::handlePaymentCompleted)

    @Bean
    fun paymentCompensationDLQReprocessor(consumer: OrderSagaConsumer): DLQMessageReprocessor =
        ListenerDLQReprocessor(
            KafkaTopics.PAYMENT_COMPENSATION,
            OrderConsumerGroups.ORDER_SAGA,
            consumer::handlePaymentCompensation
        )

    @Bean
    fun cancellationResultDLQReprocessor(consumer: OrderCancellationResultConsumer): DLQMessageReprocessor =
        ListenerDLQReprocessor(
            KafkaTopics.PAYMENT_COMPENSATION,
            OrderConsumerGroups.ORDER_CANCELLATION_RESULT,
            consumer::handleCancellationResult
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
