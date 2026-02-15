package com.hoppingmall.mall.point.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.hoppingmall.mall.payment.dto.event.PointEarnRequestEvent
import com.hoppingmall.mall.notification.enum.NotificationType
import com.hoppingmall.mall.point.domain.Point
import com.hoppingmall.mall.point.domain.PointHistory
import com.hoppingmall.mall.point.domain.PointRepository
import com.hoppingmall.mall.point.domain.PointHistoryRepository
import com.hoppingmall.mall.point.enum.PointType
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.retry.annotation.Retryable
import org.springframework.retry.annotation.Backoff
import com.hoppingmall.mall.global.common.service.TransactionalEventPublisher
import java.math.BigDecimal

@Service
@Transactional
class PointEventConsumer(
    private val pointRepository: PointRepository,
    private val pointHistoryRepository: PointHistoryRepository,
    private val transactionalEventPublisher: TransactionalEventPublisher,
    private val objectMapper: ObjectMapper
) {

    @KafkaListener(topics = ["point-earn-request"], groupId = "point-service")
    @Retryable(
        value = [DataIntegrityViolationException::class],
        maxAttempts = 5,
        backoff = Backoff(delay = 50, multiplier = 2.0)
    )
    fun handlePointEarnRequest(event: PointEarnRequestEvent) {
        try {
            if (pointHistoryRepository.existsByEventId(event.eventId)) {
                return
            }

            val point = findOrCreatePoint(event.userId)
            point.balance += event.earnAmount
            val savedPoint = pointRepository.save(point)

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
                topic = "notification",
                partitionKey = event.userId.toString()
            )
        } catch (e: Exception) {
            println("포인트 적립 처리 중 오류 발생: ${e.message}")
            throw e
        }
    }

    private fun findOrCreatePoint(userId: Long): Point {
        return try {
            pointRepository.findByUserId(userId) 
                ?: run {
                    val newPoint = Point(userId = userId)
                    pointRepository.save(newPoint)
                }
        } catch (e: DataIntegrityViolationException) {
            pointRepository.findByUserId(userId) 
                ?: throw IllegalStateException("포인트 생성/조회 실패: 사용자 $userId")
        }
    }
}
