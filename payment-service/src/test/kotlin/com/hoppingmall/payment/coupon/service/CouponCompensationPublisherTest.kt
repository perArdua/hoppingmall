package com.hoppingmall.payment.coupon.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.hoppingmall.common.KafkaTopics
import com.hoppingmall.payment.coupon.dto.event.CouponRestoreEvent
import com.hoppingmall.payment.coupon.enum.CouponRestoreReason
import com.hoppingmall.payment.coupon.metrics.CouponCompensationMetrics
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.SendResult
import java.util.concurrent.CompletableFuture

@DisplayName("CouponCompensationPublisher")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class CouponCompensationPublisherTest {

    @Mock
    private lateinit var kafkaTemplate: KafkaTemplate<String, String>

    @Mock
    private lateinit var metrics: CouponCompensationMetrics

    private val objectMapper = ObjectMapper()
        .registerModule(KotlinModule.Builder().build())
        .registerModule(JavaTimeModule())

    private val publisher: CouponCompensationPublisher by lazy {
        CouponCompensationPublisher(kafkaTemplate, objectMapper, metrics)
    }

    private fun successFuture(): CompletableFuture<SendResult<String, String>> =
        CompletableFuture.completedFuture(null)

    @Test
    fun publish_시_보상_토픽으로_이벤트와_파티션_키를_발행한다() {
        val event = CouponRestoreEvent(couponId = 1L, userId = 10L, reason = CouponRestoreReason.DB_INSERT_FAILED)
        whenever(kafkaTemplate.send(eq(KafkaTopics.COUPON_RESTORE), eq("1:10"), org.mockito.kotlin.any<String>()))
            .thenReturn(successFuture())

        publisher.publish(event)

        val captor = argumentCaptor<String>()
        verify(kafkaTemplate).send(eq(KafkaTopics.COUPON_RESTORE), eq("1:10"), captor.capture())
        val parsed = objectMapper.readValue(captor.firstValue, CouponRestoreEvent::class.java)
        assertThat(parsed.couponId).isEqualTo(1L)
        assertThat(parsed.userId).isEqualTo(10L)
        assertThat(parsed.reason).isEqualTo(CouponRestoreReason.DB_INSERT_FAILED)
        verify(metrics).recordAsyncPublished()
    }

    @Test
    fun publishToDlq_시_DLQ_토픽으로_원본_이벤트_실패사유_재시도횟수를_포함하여_발행한다() {
        val event = CouponRestoreEvent(
            couponId = 1L,
            userId = 10L,
            reason = CouponRestoreReason.COMPENSATION_FAILED,
            retryCount = 3
        )
        whenever(kafkaTemplate.send(eq(KafkaTopics.COUPON_RESTORE_DLQ), eq("1:10"), org.mockito.kotlin.any<String>()))
            .thenReturn(successFuture())

        publisher.publishToDlq(event, "Redis cluster down")

        val captor = argumentCaptor<String>()
        verify(kafkaTemplate).send(eq(KafkaTopics.COUPON_RESTORE_DLQ), eq("1:10"), captor.capture())
        @Suppress("UNCHECKED_CAST")
        val parsed = objectMapper.readValue(captor.firstValue, Map::class.java) as Map<String, Any>
        assertThat(parsed["failureReason"]).isEqualTo("Redis cluster down")
        assertThat(parsed["retryCount"]).isEqualTo(3)
        assertThat(parsed["originalEvent"]).isNotNull
        verify(metrics).recordDlq()
    }

    @Test
    fun Kafka_send_실패_시_예외를_전파한다() {
        val event = CouponRestoreEvent(couponId = 1L, userId = 10L, reason = CouponRestoreReason.DB_INSERT_FAILED)
        val failed = CompletableFuture<SendResult<String, String>>()
        failed.completeExceptionally(RuntimeException("kafka unreachable"))
        whenever(kafkaTemplate.send(eq(KafkaTopics.COUPON_RESTORE), eq("1:10"), org.mockito.kotlin.any<String>()))
            .thenReturn(failed)

        assertThatThrownBy { publisher.publish(event) }
            .hasCauseInstanceOf(RuntimeException::class.java)
    }
}
