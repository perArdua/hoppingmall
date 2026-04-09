package com.hoppingmall.dlq.publisher

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.verify
import org.springframework.kafka.core.KafkaTemplate

@DisplayName("KafkaDLQMessagePublisher")
@DisplayNameGeneration(ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class KafkaDLQMessagePublisherTest {

    @Mock
    private lateinit var kafkaTemplate: KafkaTemplate<String, Any>

    @InjectMocks
    private lateinit var kafkaDLQMessagePublisher: KafkaDLQMessagePublisher

    @Test
    fun 메시지를_Kafka로_발행하고_true를_반환한다() {
        val topic = "test-topic"
        val key = "test-key"
        val value = """{"orderId": 1}"""

        val result = kafkaDLQMessagePublisher.publish(topic, key, value)

        assertThat(result).isTrue()
        verify(kafkaTemplate).send(topic, key, value)
    }

    @Test
    fun value가_null이어도_발행하고_true를_반환한다() {
        val topic = "test-topic"
        val key = "test-key"

        val result = kafkaDLQMessagePublisher.publish(topic, key, null)

        assertThat(result).isTrue()
        verify(kafkaTemplate).send(topic, key, null)
    }
}
