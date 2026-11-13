package com.hoppingmall.payment.payment.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.hoppingmall.common.BaseEntity
import com.hoppingmall.common.KafkaTopics
import com.hoppingmall.common.event.KafkaEventMapper
import com.hoppingmall.outbox.service.TransactionalEventPublisher
import com.hoppingmall.payment.config.PaymentMetrics
import com.hoppingmall.payment.coupon.service.CouponCommandService
import com.hoppingmall.payment.payment.domain.Payment
import com.hoppingmall.payment.payment.domain.repository.PaymentRepository
import com.hoppingmall.payment.payment.dto.PaymentResult
import com.hoppingmall.payment.payment.dto.request.PaymentRequest
import com.hoppingmall.payment.payment.enum.PaymentMethod
import com.hoppingmall.payment.point.domain.Point
import com.hoppingmall.payment.point.domain.PointHistory
import com.hoppingmall.payment.point.domain.PointHistoryRepository
import com.hoppingmall.payment.point.domain.PointRepository
import com.hoppingmall.payment.point.service.PointDomainService
import com.hoppingmall.payment.point.service.PointEventConsumer
import com.hoppingmall.payment.point.service.strategy.PointEarnRateStrategy
import com.hoppingmall.payment.support.FakeOrderSummaryQueryPort
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.whenever
import org.springframework.cache.CacheManager
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.SimpleTransactionStatus
import java.math.BigDecimal

@DisplayName("한 결제에서 나가는 알림 이벤트의 eventId")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class PaymentNotificationEventIdTest {

    @Mock
    private lateinit var transactionalEventPublisher: TransactionalEventPublisher

    @Mock
    private lateinit var paymentRepository: PaymentRepository

    @Mock
    private lateinit var paymentService: PaymentService

    @Mock
    private lateinit var couponCommandService: CouponCommandService

    @Mock
    private lateinit var pointEarnRateStrategy: PointEarnRateStrategy

    @Mock
    private lateinit var transactionManager: PlatformTransactionManager

    @Mock
    private lateinit var pointRepository: PointRepository

    @Mock
    private lateinit var pointHistoryRepository: PointHistoryRepository

    @Mock
    private lateinit var cacheManager: CacheManager

    @Mock
    private lateinit var pointDomainService: PointDomainService

    private val globalObjectMapper: ObjectMapper = jacksonObjectMapper()
        .registerModule(JavaTimeModule())
        .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
    private val messageWriter: ObjectMapper = jacksonObjectMapper()
    private val published = mutableListOf<PublishedEvent>()
    private val savedHistories = mutableListOf<PointHistory>()
    private var storedPayment: Payment? = null
    private val orderSummaryQueryPort = FakeOrderSummaryQueryPort()

    private lateinit var commandService: PaymentCommandServiceImpl
    private lateinit var pointEventConsumer: PointEventConsumer

    @BeforeEach
    fun setUp() {
        orderSummaryQueryPort.put(ORDER_ID, USER_ID, BigDecimal("30000"))
        doAnswer { invocation ->
            published.add(
                PublishedEvent(
                    eventType = invocation.arguments[2] as String,
                    eventData = invocation.arguments[3],
                    topic = invocation.arguments[4] as String
                )
            )
            null
        }.whenever(transactionalEventPublisher).publishEvent(any(), any(), any(), any(), any(), anyOrNull())

        Mockito.lenient().`when`(transactionManager.getTransaction(anyOrNull())).thenReturn(SimpleTransactionStatus())
        Mockito.lenient().`when`(paymentRepository.save(any<Payment>())).thenAnswer { invocation ->
            val payment = invocation.arguments[0] as Payment
            if (payment.id == null) {
                val idField = BaseEntity::class.java.getDeclaredField("id")
                idField.isAccessible = true
                idField.set(payment, PAYMENT_ID)
            }
            storedPayment = payment
            payment
        }
        Mockito.lenient().`when`(paymentRepository.findByIdForUpdate(PAYMENT_ID)).thenAnswer { storedPayment }
        Mockito.lenient().`when`(paymentService.processPayment(any())).thenAnswer { invocation ->
            PaymentResult.success(invocation.arguments[0] as Payment, TRANSACTION_ID)
        }
        Mockito.lenient().`when`(pointEarnRateStrategy.getEarnRate(USER_ID)).thenReturn(BigDecimal("0.01"))

        Mockito.lenient().`when`(pointHistoryRepository.existsByEventId(any())).thenAnswer { invocation ->
            savedHistories.any { it.eventId == invocation.arguments[0] }
        }
        Mockito.lenient().`when`(pointHistoryRepository.save(any<PointHistory>())).thenAnswer { invocation ->
            (invocation.arguments[0] as PointHistory).also { savedHistories.add(it) }
        }
        Mockito.lenient().`when`(pointDomainService.findOrCreatePoint(any())).thenAnswer { invocation ->
            Point(userId = invocation.arguments[0] as Long).also { ReflectionTestUtils.setField(it, "id", 1L) }
        }
        Mockito.lenient().`when`(pointRepository.save(any<Point>())).thenAnswer { it.arguments[0] }

        val paymentEventService = PaymentEventService(KafkaPaymentEventPublisher(transactionalEventPublisher))
        val paymentNotificationService = PaymentNotificationService(
            transactionalEventPublisher,
            NotificationEventFactory(),
            globalObjectMapper
        )
        commandService = PaymentCommandServiceImpl(
            paymentRepository,
            paymentService,
            paymentEventService,
            paymentNotificationService,
            couponCommandService,
            PaymentMetrics(SimpleMeterRegistry()),
            pointEarnRateStrategy,
            orderSummaryQueryPort,
            transactionManager
        )
        pointEventConsumer = PointEventConsumer(
            pointRepository,
            pointHistoryRepository,
            transactionalEventPublisher,
            globalObjectMapper,
            KafkaEventMapper(globalObjectMapper),
            cacheManager,
            pointDomainService
        )
    }

    private fun payAndDeliverPointEarnRequest() {
        commandService.processPayment(
            PaymentRequest(orderId = ORDER_ID, amount = BigDecimal("30000"), method = PaymentMethod.CREDIT_CARD),
            USER_ID
        )
        pointEventConsumer.handlePointEarnRequest(pointEarnRequestMessage())
    }

    private fun pointEarnRequestMessage(): String {
        val request = published.single { it.eventType == "PointEarnRequested" }
        assertThat(request.topic).isEqualTo(KafkaTopics.POINT_EARN_REQUEST)
        return messageWriter.writeValueAsString(request.eventData)
    }

    private fun notificationEventId(eventType: String): String {
        val notification = published.single { it.eventType == eventType }
        assertThat(notification.topic).isEqualTo(KafkaTopics.NOTIFICATION)
        @Suppress("UNCHECKED_CAST")
        return (notification.eventData as Map<String, Any?>)["eventId"] as String
    }

    @Test
    fun 결제_완료_알림과_포인트_적립_알림은_서로_다른_eventId를_가진다() {
        payAndDeliverPointEarnRequest()

        val paymentCompleted = notificationEventId("PaymentCompletedNotificationRequested")
        val pointEarned = notificationEventId("PointEarnedNotificationRequested")

        assertThat(pointEarned).isNotEqualTo(paymentCompleted)
    }

    @Test
    fun 결제_완료부터_취소까지_한_결제에서_나간_알림의_eventId는_모두_다르다() {
        payAndDeliverPointEarnRequest()
        commandService.cancelPayment(PAYMENT_ID, USER_ID)

        val notificationEventIds = published
            .filter { it.topic == KafkaTopics.NOTIFICATION }
            .map {
                @Suppress("UNCHECKED_CAST")
                (it.eventData as Map<String, Any?>)["eventId"] as String
            }

        assertThat(notificationEventIds).hasSize(3).doesNotHaveDuplicates()
    }

    @Test
    fun 같은_적립_요청이_다시_와도_포인트_적립_알림은_한_번만_나간다() {
        payAndDeliverPointEarnRequest()
        pointEventConsumer.handlePointEarnRequest(pointEarnRequestMessage())

        assertThat(published.filter { it.eventType == "PointEarnedNotificationRequested" }).hasSize(1)
        assertThat(savedHistories).hasSize(1)
    }

    private data class PublishedEvent(
        val eventType: String,
        val eventData: Any,
        val topic: String
    )

    companion object {
        private const val PAYMENT_ID = 501L
        private const val ORDER_ID = 9001L
        private const val USER_ID = 77L
        private const val TRANSACTION_ID = "TXN-E2-1"
    }
}
