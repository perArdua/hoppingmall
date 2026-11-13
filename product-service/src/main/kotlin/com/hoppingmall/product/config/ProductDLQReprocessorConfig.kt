package com.hoppingmall.product.config

import com.hoppingmall.common.KafkaTopics
import com.hoppingmall.dlq.reprocess.DLQMessageReprocessor
import com.hoppingmall.product.statistics.service.StatisticsEventConsumer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class ProductDLQReprocessorConfig {

    @Bean
    fun statisticsPaymentDLQReprocessor(consumer: StatisticsEventConsumer): DLQMessageReprocessor =
        ListenerDLQReprocessor(KafkaTopics.PAYMENT, ProductConsumerGroups.STATISTICS, consumer::handlePaymentCompleted)

    @Bean
    fun statisticsCompensationDLQReprocessor(consumer: StatisticsEventConsumer): DLQMessageReprocessor =
        ListenerDLQReprocessor(
            KafkaTopics.PAYMENT_COMPENSATION,
            ProductConsumerGroups.STATISTICS_COMPENSATION,
            consumer::handleCompensationEvent
        )

    @Bean
    fun statisticsReversalDLQReprocessor(consumer: StatisticsEventConsumer): DLQMessageReprocessor =
        ListenerDLQReprocessor(
            KafkaTopics.PAYMENT_REVERSAL,
            ProductConsumerGroups.STATISTICS_COMPENSATION,
            consumer::handlePaymentReversal
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
