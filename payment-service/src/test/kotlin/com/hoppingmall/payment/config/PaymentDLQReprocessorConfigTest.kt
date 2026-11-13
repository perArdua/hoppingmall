package com.hoppingmall.payment.config

import com.hoppingmall.common.KafkaTopics
import com.hoppingmall.dlq.reprocess.DLQReprocessors
import com.hoppingmall.payment.payment.service.PaymentCompensationConsumer
import com.hoppingmall.payment.payment.service.PaymentEventConsumer
import com.hoppingmall.payment.point.service.PointEventConsumer
import com.hoppingmall.payment.refund.service.RefundCompletionConsumer
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

@DisplayName("결제 서비스 리스너별 DLQ 직접 재처리기")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class PaymentDLQReprocessorConfigTest {

    @Mock
    private lateinit var paymentEventConsumer: PaymentEventConsumer

    @Mock
    private lateinit var paymentCompensationConsumer: PaymentCompensationConsumer

    @Mock
    private lateinit var pointEventConsumer: PointEventConsumer

    @Mock
    private lateinit var refundCompletionConsumer: RefundCompletionConsumer

    private lateinit var reprocessors: DLQReprocessors

    @BeforeEach
    fun setUp() {
        val config = PaymentDLQReprocessorConfig()
        reprocessors = DLQReprocessors(
            listOf(
                config.paymentEventDLQReprocessor(paymentEventConsumer, KafkaTopics.PAYMENT),
                config.paymentCompensationDLQReprocessor(paymentCompensationConsumer),
                config.paymentReversalDLQReprocessor(paymentCompensationConsumer),
                config.pointEarnDLQReprocessor(pointEventConsumer),
                config.refundCompletionDLQReprocessor(refundCompletionConsumer)
            )
        )
    }

    @Test
    fun 기본_컨테이너를_쓰는_결제_서비스_리스너는_모두_자기_토픽과_소비_그룹의_처리기를_가진다() {
        val listeners = listOf(
            PaymentEventConsumer::class.java,
            PaymentCompensationConsumer::class.java,
            PointEventConsumer::class.java,
            RefundCompletionConsumer::class.java
        ).flatMap { type ->
            type.declaredMethods.mapNotNull { it.getAnnotation(KafkaListener::class.java) }
        }.filter { it.containerFactory.isEmpty() }
            .flatMap { listener -> listener.topics.map { resolveDefault(it) to listener.groupId } }

        assertThat(listeners).hasSize(5)
        assertThat(listeners).allSatisfy { (topic, group) ->
            assertThat(reprocessors.supports(topic, group)).describedAs("$topic / $group").isTrue()
        }
    }

    @Test
    fun DLQ_재처리는_그_토픽과_그룹의_리스너_메서드를_메시지_본문으로_부른다() {
        reprocessors.reprocess(KafkaTopics.PAYMENT, PaymentConsumerGroups.PAYMENT_EVENT, "1", "payment-body")
        reprocessors.reprocess(KafkaTopics.PAYMENT_COMPENSATION, PaymentConsumerGroups.PAYMENT_COMPENSATION, "1", "compensation-body")
        reprocessors.reprocess(KafkaTopics.PAYMENT_REVERSAL, PaymentConsumerGroups.PAYMENT_COMPENSATION, "1", "reversal-body")
        reprocessors.reprocess(KafkaTopics.POINT_EARN_REQUEST, PaymentConsumerGroups.POINT_EARN, "1", "point-body")
        reprocessors.reprocess(KafkaTopics.REFUND_COMPLETION, PaymentConsumerGroups.REFUND_COMPLETION, "1", "refund-body")

        verify(paymentEventConsumer).handlePaymentEvent("payment-body")
        verify(paymentCompensationConsumer).handleCompensationEvent("compensation-body")
        verify(paymentCompensationConsumer).handlePaymentReversal("reversal-body")
        verify(pointEventConsumer).handlePointEarnRequest("point-body")
        verify(refundCompletionConsumer).handleRefundCompletionEvent("refund-body")
    }

    @Test
    fun 본문이_없는_DLQ_메시지는_리스너를_부르지_않고_실패한다() {
        assertThatThrownBy {
            reprocessors.reprocess(KafkaTopics.REFUND_COMPLETION, PaymentConsumerGroups.REFUND_COMPLETION, "1", null)
        }.isInstanceOf(IllegalArgumentException::class.java)

        verifyNoInteractions(refundCompletionConsumer)
    }

    private fun resolveDefault(topic: String): String =
        PLACEHOLDER.matchEntire(topic)?.groupValues?.get(1) ?: topic

    companion object {
        private val PLACEHOLDER = Regex("""\$\{[^:}]+:([^}]+)}""")
    }
}
