package com.hoppingmall.product.config

import com.hoppingmall.common.KafkaTopics
import com.hoppingmall.dlq.domain.DeadLetterMessage
import com.hoppingmall.dlq.service.DLQCommandService
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.kafka.support.KafkaUtils

@DisplayName("상품 Kafka 메시지 회수기가 DLQ에 실패한 소비 그룹을 남긴다")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class KafkaConsumerConfigDeadLetterRecovererTest {

    @Mock
    private lateinit var dlqCommandService: DLQCommandService

    @Test
    fun 재시도를_다_쓴_메시지는_그_메시지를_처리하던_소비_그룹과_함께_DLQ에_저장된다() {
        val recoverer = KafkaConsumerConfig(dlqCommandService).deadLetterRecoverer()
        val failures = listOf(
            KafkaTopics.PAYMENT to "statistics-service",
            KafkaTopics.PAYMENT_COMPENSATION to "statistics-compensation-service",
            KafkaTopics.PAYMENT_REVERSAL to "statistics-compensation-service"
        )

        failures.forEach { (topic, group) ->
            KafkaUtils.setConsumerGroupId(group)
            try {
                recoverer.accept(ConsumerRecord(topic, 0, 7L, "100", "{}"), RuntimeException("재시도 소진"))
            } finally {
                KafkaUtils.clearConsumerGroupId()
            }
        }

        val saved = argumentCaptor<DeadLetterMessage>()
        verify(dlqCommandService, times(failures.size)).saveDLQMessage(saved.capture())
        assertThat(saved.allValues.map { it.originalTopic to it.consumerGroup }).containsExactlyElementsOf(failures)
    }

    @Test
    fun 컨슈머_스레드_밖에서_회수되면_소비_그룹_없이_저장한다() {
        val recoverer = KafkaConsumerConfig(dlqCommandService).deadLetterRecoverer()

        recoverer.accept(ConsumerRecord(KafkaTopics.PAYMENT_REVERSAL, 0, 7L, "100", "{}"), RuntimeException("재시도 소진"))

        val saved = argumentCaptor<DeadLetterMessage>()
        verify(dlqCommandService).saveDLQMessage(saved.capture())
        assertThat(saved.firstValue.consumerGroup).isNull()
    }
}
