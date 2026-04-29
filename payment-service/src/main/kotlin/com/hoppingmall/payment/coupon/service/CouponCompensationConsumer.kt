package com.hoppingmall.payment.coupon.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.hoppingmall.common.KafkaTopics
import com.hoppingmall.payment.coupon.dto.event.CouponRestoreEvent
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Service

@Service
class CouponCompensationConsumer(
    private val processor: CouponCompensationProcessor,
    private val publisher: CouponCompensationPublisher,
    private val objectMapper: ObjectMapper
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @KafkaListener(
        topics = [KafkaTopics.COUPON_RESTORE],
        groupId = "coupon-compensation-consumer",
        containerFactory = "couponCompensationListenerContainerFactory"
    )
    fun handle(message: String) {
        val event = objectMapper.readValue(message, CouponRestoreEvent::class.java)
        try {
            processor.process(event)
        } catch (e: Exception) {
            handleFailure(event, e)
        }
    }

    private fun handleFailure(event: CouponRestoreEvent, cause: Exception) {
        if (event.retryCount >= MAX_RETRIES) {
            log.error(
                "쿠폰 보상 재시도 한계 도달, DLQ 이동: couponId={}, userId={}, retryCount={}",
                event.couponId, event.userId, event.retryCount, cause
            )
            publisher.publishToDlq(event, cause.message ?: cause.javaClass.simpleName)
            return
        }
        val nextEvent = event.nextRetry()
        log.warn(
            "쿠폰 보상 재시도 발행: couponId={}, userId={}, nextRetryCount={}, error={}",
            event.couponId, event.userId, nextEvent.retryCount, cause.message
        )
        publisher.publish(nextEvent)
    }

    companion object {
        const val MAX_RETRIES = 3
    }
}
