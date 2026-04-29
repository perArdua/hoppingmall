package com.hoppingmall.payment.coupon.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.hoppingmall.common.KafkaTopics
import com.hoppingmall.payment.coupon.dto.event.CouponRestoreEvent
import com.hoppingmall.payment.coupon.metrics.CouponCompensationMetrics
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit

@Service
class CouponCompensationPublisher(
    @Qualifier("couponCompensationKafkaTemplate")
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val objectMapper: ObjectMapper,
    private val metrics: CouponCompensationMetrics
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun publish(event: CouponRestoreEvent) {
        val payload = objectMapper.writeValueAsString(event)
        val key = partitionKey(event.couponId, event.userId)
        kafkaTemplate.send(KafkaTopics.COUPON_RESTORE, key, payload)
            .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        metrics.recordAsyncPublished()
        log.info(
            "쿠폰 보상 이벤트 발행: couponId={}, userId={}, reason={}, retryCount={}",
            event.couponId, event.userId, event.reason, event.retryCount
        )
    }

    fun publishToDlq(event: CouponRestoreEvent, failureReason: String) {
        val dlqPayload = mapOf(
            "originalEvent" to event,
            "failureReason" to failureReason,
            "retryCount" to event.retryCount,
            "movedAt" to java.time.LocalDateTime.now().toString()
        )
        val payload = objectMapper.writeValueAsString(dlqPayload)
        val key = partitionKey(event.couponId, event.userId)
        kafkaTemplate.send(KafkaTopics.COUPON_RESTORE_DLQ, key, payload)
            .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        metrics.recordDlq()
        log.error(
            "쿠폰 보상 DLQ 이동: couponId={}, userId={}, retryCount={}, reason={}",
            event.couponId, event.userId, event.retryCount, failureReason
        )
    }

    private fun partitionKey(couponId: Long, userId: Long) = "$couponId:$userId"

    companion object {
        private const val SEND_TIMEOUT_SECONDS = 5L
    }
}
