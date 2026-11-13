package com.hoppingmall.payment.payment.service.strategy

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.hoppingmall.payment.payment.domain.CompensationEventLog
import com.hoppingmall.payment.payment.domain.CompensationEventLogStatus
import com.hoppingmall.payment.payment.domain.Payment
import com.hoppingmall.payment.payment.domain.repository.PaymentRepository
import com.hoppingmall.payment.payment.enum.PaymentMethod
import com.hoppingmall.payment.payment.enum.PaymentStatus
import com.hoppingmall.payment.payment.service.CompensationEventLogService
import com.hoppingmall.payment.payment.service.RefundPointsService
import com.hoppingmall.payment.point.domain.PointHistoryRepository
import com.hoppingmall.payment.point.domain.PointRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.Optional

@DisplayName("결제 취소 보상 실패 로그 수준")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class PaymentCancelledCompensationRetryLogTest {

    @Mock
    private lateinit var compensationEventLogService: CompensationEventLogService

    @Mock
    private lateinit var pointRepository: PointRepository

    @Mock
    private lateinit var pointHistoryRepository: PointHistoryRepository

    @Mock
    private lateinit var paymentRepository: PaymentRepository

    private val objectMapper = jacksonObjectMapper()

    private val handlerLogger = LoggerFactory.getLogger(PaymentCancelledCompensationHandler::class.java) as Logger

    private val appender = ListAppender<ILoggingEvent>()

    @BeforeEach
    fun attachAppender() {
        appender.start()
        handlerLogger.addAppender(appender)
    }

    @AfterEach
    fun detachAppender() {
        handlerLogger.detachAppender(appender)
    }

    private fun handler() = PaymentCancelledCompensationHandler(
        compensationEventLogService,
        RefundPointsService(pointRepository, pointHistoryRepository, paymentRepository),
        objectMapper
    )

    private fun cancelledNode(): JsonNode = objectMapper.valueToTree(
        mapOf(
            "eventType" to "PaymentCancelled",
            "eventId" to "payment-cancelled-100",
            "paymentId" to 100L,
            "orderId" to 200L,
            "userId" to 1L,
            "amount" to "50000",
            "transactionId" to "txn-807"
        )
    )

    private fun pendingLog() = CompensationEventLog(
        eventId = "payment-cancelled-100",
        compensationType = "PAYMENT_CANCELLED",
        paymentId = 100L,
        orderId = 200L,
        status = CompensationEventLogStatus.PENDING
    )

    private fun cancelledPayment(): Payment {
        val payment = Payment.create(orderId = 200L, userId = 1L, amount = BigDecimal("50000"), method = PaymentMethod.CREDIT_CARD)
        payment.updateStatus(PaymentStatus.SUCCESS, transactionId = "txn-807", completedAt = LocalDateTime.now())
        payment.updateStatus(PaymentStatus.CANCELLED, transactionId = "txn-807", completedAt = payment.completedAt)
        return payment
    }

    private fun failureLogLevels(): List<Level> =
        appender.list.filter { it.formattedMessage.contains("결제 취소 보상") && it.formattedMessage.contains("payment-cancelled-100") }
            .map { it.level }

    @Test
    fun 적립_이력이_아직_없어_다시_시도되는_회수_실패는_ERROR가_아닌_WARN으로_남기고_예외는_그대로_던진다() {
        whenever(compensationEventLogService.saveIfAbsent(any(), any(), any(), any())).thenReturn(pendingLog())
        whenever(paymentRepository.findById(100L)).thenReturn(Optional.of(cancelledPayment()))

        val result = runCatching { handler().handle(cancelledNode()) }

        assertThat(result.exceptionOrNull()).isInstanceOf(IllegalStateException::class.java)
        assertThat(failureLogLevels()).containsExactly(Level.WARN)
    }

    @Test
    fun 다른_이유의_보상_실패는_ERROR로_남긴다() {
        whenever(compensationEventLogService.saveIfAbsent(any(), any(), any(), any()))
            .thenThrow(RuntimeException("DB 연결 끊김"))

        val result = runCatching { handler().handle(cancelledNode()) }

        assertThat(result.exceptionOrNull()).isInstanceOf(RuntimeException::class.java)
        assertThat(failureLogLevels()).containsExactly(Level.ERROR)
    }
}
