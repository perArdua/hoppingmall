package com.hoppingmall.mall.point.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.hoppingmall.mall.global.common.service.TransactionalEventPublisher
import com.hoppingmall.mall.payment.dto.event.PointEarnRequestEvent
import com.hoppingmall.mall.point.domain.Point
import com.hoppingmall.mall.point.domain.PointHistory
import com.hoppingmall.mall.point.domain.PointHistoryRepository
import com.hoppingmall.mall.point.domain.PointRepository
import com.hoppingmall.mall.point.enum.PointType
import com.hoppingmall.mall.support.withId
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Spy
import org.mockito.junit.jupiter.MockitoExtension
import java.math.BigDecimal

@ExtendWith(MockitoExtension::class)
@DisplayName("PointEventConsumer 단위 테스트")
@DisplayNameGeneration(ReplaceUnderscores::class)
class PointEventConsumerTest {

    @Mock
    private lateinit var pointRepository: PointRepository
    
    @Mock
    private lateinit var pointHistoryRepository: PointHistoryRepository
    
    @Mock
    private lateinit var transactionalEventPublisher: TransactionalEventPublisher

    @Spy
    private val objectMapper = ObjectMapper()
    
    @InjectMocks
    private lateinit var pointEventConsumer: PointEventConsumer

    @Test
    fun 신규_사용자_포인트_적립() {
        val userId = 1L
        val earnAmount = BigDecimal("1000")
        val event = PointEarnRequestEvent(
            eventId = "EVENT-1",
            userId = userId,
            orderId = 123L,
            paymentId = 456L,
            earnAmount = earnAmount,
            reason = "주문 완료 포인트 적립"
        )
        
        whenever(pointRepository.findByUserId(userId)).thenReturn(null)
        val newPoint = Point(userId = userId).withId(1L)
        whenever(pointRepository.save(any<Point>())).thenReturn(newPoint)
        
        val savedHistory = PointHistory(
            userId = event.userId,
            amount = event.earnAmount,
            type = PointType.EARN,
            reason = event.reason,
            orderId = event.orderId,
            paymentId = event.paymentId,
            eventId = event.eventId
        ).withId(1L)
        whenever(pointHistoryRepository.save(any<PointHistory>())).thenReturn(savedHistory)
        
        pointEventConsumer.handlePointEarnRequest(event)
        
        verify(pointRepository).findByUserId(userId)
        verify(pointRepository, times(2)).save(any<Point>())
        verify(pointHistoryRepository).save(any<PointHistory>())
        verify(transactionalEventPublisher).publishEvent(
            eq("Point"),
            eq("1"),
            eq("PointEarnedNotificationRequested"),
            argThat { eventData ->
                val data = eventData as Map<String, Any>
                val metadata = data["metadata"] as String
                val metadataMap = objectMapper.readValue(metadata, Map::class.java)
                metadataMap["orderId"].toString() == "123" &&
                    metadataMap["paymentId"].toString() == "456"
            },
            eq("notification"),
            eq(userId.toString())
        )
    }

    @Test
    fun 기존_사용자_포인트_누적_적립() {
        val userId = 2L
        val initialBalance = BigDecimal("500")
        val earnAmount = BigDecimal("1500")
        
        val existingPoint = Point(userId = userId, balance = initialBalance).withId(2L)
        whenever(pointRepository.findByUserId(userId)).thenReturn(existingPoint)
        whenever(pointRepository.save(any<Point>())).thenReturn(existingPoint)
        
        val event = PointEarnRequestEvent(
            eventId = "EVENT-2",
            userId = userId,
            orderId = 789L,
            paymentId = 321L,
            earnAmount = earnAmount,
            reason = "추가 주문 포인트"
        )
        
        val savedHistory2 = PointHistory(
            userId = event.userId,
            amount = event.earnAmount,
            type = PointType.EARN,
            reason = event.reason,
            orderId = event.orderId,
            paymentId = event.paymentId,
            eventId = event.eventId
        ).withId(2L)
        whenever(pointHistoryRepository.save(any<PointHistory>())).thenReturn(savedHistory2)
        
        pointEventConsumer.handlePointEarnRequest(event)
        
        verify(pointRepository).findByUserId(userId)
        verify(pointRepository).save(any<Point>())
        verify(pointHistoryRepository).save(any<PointHistory>())
        verify(transactionalEventPublisher).publishEvent(
            eq("Point"),
            eq("2"),
            eq("PointEarnedNotificationRequested"),
            argThat { eventData ->
                val data = eventData as Map<String, Any>
                data["eventId"] == "EVENT-2"
            },
            eq("notification"),
            eq(userId.toString())
        )
    }

    @Test
    fun 중복_이벤트는_포인트_적립을_건너뛴다() {
        val event = PointEarnRequestEvent(
            eventId = "EVENT-3",
            userId = 3L,
            orderId = 111L,
            paymentId = 222L,
            earnAmount = BigDecimal("500"),
            reason = "중복 테스트"
        )

        whenever(pointHistoryRepository.existsByEventId(event.eventId)).thenReturn(true)

        pointEventConsumer.handlePointEarnRequest(event)

        verify(pointRepository, never()).save(any<Point>())
        verify(pointHistoryRepository, never()).save(any<PointHistory>())
        verify(transactionalEventPublisher, never()).publishEvent(any(), any(), any(), any(), any(), any())
    }
}
