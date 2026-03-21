package com.hoppingmall.payment.point.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.hoppingmall.common.KafkaTopics
import com.hoppingmall.payment.common.NotificationType
import com.hoppingmall.payment.outbox.service.TransactionalEventPublisher
import com.hoppingmall.payment.payment.dto.event.PointEarnRequestEvent
import com.hoppingmall.payment.point.domain.PointHistory
import com.hoppingmall.payment.point.domain.PointHistoryRepository
import com.hoppingmall.payment.point.domain.PointRepository
import com.hoppingmall.payment.point.enum.PointType
import org.slf4j.LoggerFactory
import org.springframework.cache.CacheManager
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class PointEventConsumer(
    private val pointRepository: PointRepository,
    private val pointHistoryRepository: PointHistoryRepository,
    private val transactionalEventPublisher: TransactionalEventPublisher,
    private val objectMapper: ObjectMapper,
    private val cacheManager: CacheManager,
    private val pointDomainService: PointDomainService
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @KafkaListener(topics = [KafkaTopics.POINT_EARN_REQUEST], groupId = "point-service")
    fun handlePointEarnRequest(message: String) {
        val event = objectMapper.readValue(message, PointEarnRequestEvent::class.java)
        try {
            if (pointHistoryRepository.existsByEventId(event.eventId)) {
                log.info("이미 처리된 포인트 이벤트: eventId={}", event.eventId)
                return
            }

            val point = pointDomainService.findOrCreatePoint(event.userId)
            point.balance += event.earnAmount
            val savedPoint = pointRepository.save(point)
            cacheManager.getCache("point-balance")?.evict(event.userId)

            pointHistoryRepository.save(
                PointHistory(
                    userId = event.userId,
                    amount = event.earnAmount,
                    type = PointType.EARN,
                    reason = event.reason,
                    orderId = event.orderId,
                    paymentId = event.paymentId,
                    eventId = event.eventId
                )
            )

            val metadata = objectMapper.writeValueAsString(
                mapOf(
                    "orderId" to event.orderId,
                    "paymentId" to event.paymentId,
                    "earnAmount" to event.earnAmount.toString(),
                    "reason" to (event.reason ?: "포인트 적립"),
                    "currentBalance" to savedPoint.balance.toString()
                )
            )

            transactionalEventPublisher.publishEvent(
                aggregateType = "Point",
                aggregateId = savedPoint.id!!.toString(),
                eventType = "PointEarnedNotificationRequested",
                eventData = mapOf(
                    "eventId" to event.eventId,
                    "userId" to event.userId,
                    "type" to NotificationType.POINT_EARNED.toString(),
                    "title" to "포인트가 적립되었습니다",
                    "content" to "주문번호 ${event.orderId}의 포인트 ${event.earnAmount}점이 적립되었습니다. 현재 잔액: ${savedPoint.balance}점",
                    "metadata" to metadata
                ),
                topic = KafkaTopics.NOTIFICATION,
                partitionKey = event.userId.toString()
            )
        } catch (e: Exception) {
            log.error("포인트 적립 처리 실패: userId={}, orderId={}, 오류={}", event.userId, event.orderId, e.message)
            throw e
        }
    }
}
