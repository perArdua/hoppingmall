package com.hoppingmall.product.config

import com.hoppingmall.common.KafkaTopics
import com.hoppingmall.dlq.reprocess.DLQReprocessors
import com.hoppingmall.product.statistics.service.StatisticsEventConsumer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.springframework.kafka.annotation.KafkaListener

@DisplayName("상품 서비스 리스너별 DLQ 직접 재처리기")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class ProductDLQReprocessorConfigTest {

    @Mock
    private lateinit var statisticsEventConsumer: StatisticsEventConsumer

    private lateinit var reprocessors: DLQReprocessors

    @BeforeEach
    fun setUp() {
        val config = ProductDLQReprocessorConfig()
        reprocessors = DLQReprocessors(
            listOf(
                config.statisticsPaymentDLQReprocessor(statisticsEventConsumer),
                config.statisticsCompensationDLQReprocessor(statisticsEventConsumer),
                config.statisticsReversalDLQReprocessor(statisticsEventConsumer)
            )
        )
    }

    @Test
    fun 상품_서비스의_Kafka_리스너는_모두_자기_토픽과_소비_그룹의_처리기를_가진다() {
        val listeners = StatisticsEventConsumer::class.java.declaredMethods
            .mapNotNull { it.getAnnotation(KafkaListener::class.java) }
            .flatMap { listener -> listener.topics.map { it to listener.groupId } }

        assertThat(listeners).hasSize(3)
        assertThat(listeners).allSatisfy { (topic, group) ->
            assertThat(reprocessors.supports(topic, group)).describedAs("$topic / $group").isTrue()
        }
    }

    @Test
    fun DLQ_재처리는_그_토픽과_그룹의_리스너_메서드를_메시지_본문으로_부른다() {
        reprocessors.reprocess(KafkaTopics.PAYMENT, ProductConsumerGroups.STATISTICS, "1", "payment-body")
        reprocessors.reprocess(KafkaTopics.PAYMENT_COMPENSATION, ProductConsumerGroups.STATISTICS_COMPENSATION, "1", "compensation-body")
        reprocessors.reprocess(KafkaTopics.PAYMENT_REVERSAL, ProductConsumerGroups.STATISTICS_COMPENSATION, "1", "reversal-body")

        verify(statisticsEventConsumer).handlePaymentCompleted("payment-body")
        verify(statisticsEventConsumer).handleCompensationEvent("compensation-body")
        verify(statisticsEventConsumer).handlePaymentReversal("reversal-body")
    }

    @Test
    fun 본문이_없는_DLQ_메시지는_리스너를_부르지_않고_실패한다() {
        assertThatThrownBy {
            reprocessors.reprocess(KafkaTopics.PAYMENT_REVERSAL, ProductConsumerGroups.STATISTICS_COMPENSATION, "1", null)
        }.isInstanceOf(IllegalArgumentException::class.java)

        verifyNoInteractions(statisticsEventConsumer)
    }
}
