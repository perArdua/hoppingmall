package com.hoppingmall.payment.payment.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.hoppingmall.common.UserPrincipal
import com.hoppingmall.idempotency.IdempotencyAspect
import com.hoppingmall.idempotency.IdempotencyKeyMissingException
import com.hoppingmall.idempotency.IdempotencyRecord
import com.hoppingmall.idempotency.IdempotencyService
import com.hoppingmall.payment.payment.dto.request.PaymentRequest
import com.hoppingmall.payment.payment.dto.response.PaymentResponse
import com.hoppingmall.payment.payment.enum.PaymentMethod
import com.hoppingmall.payment.payment.enum.PaymentStatus
import com.hoppingmall.payment.payment.service.PaymentCommandService
import com.hoppingmall.payment.payment.service.PaymentQueryService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.redisson.api.RLock
import org.redisson.api.RedissonClient
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory
import org.springframework.http.ResponseEntity
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap

@DisplayName("결제 요청의 멱등성 키 응답 재생")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class PaymentIdempotencyReplayTest {

    private val paymentCommandService: PaymentCommandService = mock()
    private val paymentQueryService: PaymentQueryService = mock()
    private val idempotencyService: IdempotencyService = mock()
    private val redissonClient: RedissonClient = mock()
    private val objectMapper: ObjectMapper = jacksonObjectMapper().registerModule(JavaTimeModule())
    private val records = ConcurrentHashMap<String, IdempotencyRecord>()

    private val controller: PaymentController = AspectJProxyFactory(PaymentController(paymentCommandService, paymentQueryService))
        .apply {
            isProxyTargetClass = true
            addAspect(IdempotencyAspect(idempotencyService, redissonClient, objectMapper))
        }
        .getProxy()

    private val buyer = UserPrincipal.of(BUYER_ID, "BUYER")
    private val request = PaymentRequest(orderId = ORDER_ID, amount = BigDecimal("30000.00"), method = PaymentMethod.CREDIT_CARD)

    @BeforeEach
    fun setUp() {
        val lock: RLock = mock()
        whenever(redissonClient.getLock(any<String>())).thenReturn(lock)
        whenever(lock.tryLock(any(), any(), any())).thenReturn(true)
        whenever(lock.isHeldByCurrentThread).thenReturn(true)
        whenever(idempotencyService.findByKey(any())).thenAnswer { records[it.getArgument<String>(0)] }
        whenever(idempotencyService.save(any(), any(), any(), any(), any(), any(), any())).thenAnswer {
            val record = IdempotencyRecord(
                idempotencyKey = it.getArgument(0),
                httpMethod = it.getArgument(1),
                endpoint = it.getArgument(2),
                responseStatus = it.getArgument(3),
                responseBody = it.getArgument(4),
                expiresAt = LocalDateTime.now().plusHours(it.getArgument<Long>(6)),
                requestFingerprint = it.getArgument(5)
            )
            records[record.idempotencyKey] = record
            record
        }
    }

    @AfterEach
    fun tearDown() {
        RequestContextHolder.resetRequestAttributes()
    }

    @Test
    fun 같은_키로_결제를_두_번_요청하면_두_번째는_첫_응답을_그대로_받고_결제는_한_번만_실행된다() {
        whenever(paymentCommandService.processPayment(request, BUYER_ID)).thenReturn(payment(PaymentStatus.SUCCESS))

        val first = call("payment-key") { controller.processPayment(request, buyer) }
        val second = call("payment-key") { controller.processPayment(request, buyer) }

        verify(paymentCommandService, times(1)).processPayment(request, BUYER_ID)
        assertSameReplay(asResponse(first), asResponse(second))
    }

    @Test
    fun 같은_키로_실패한_결제를_다시_요청하면_첫_실패_응답을_그대로_받고_결제는_다시_실행되지_않는다() {
        whenever(paymentCommandService.processPayment(request, BUYER_ID)).thenReturn(payment(PaymentStatus.FAILED))

        val first = call("failed-key") { controller.processPayment(request, buyer) }
        val second = call("failed-key") { controller.processPayment(request, buyer) }

        verify(paymentCommandService, times(1)).processPayment(request, BUYER_ID)
        assertSameReplay(asResponse(first), asResponse(second))
        assertThat(objectMapper.writeValueAsString(asResponse(second).body)).contains("\"status\":\"FAILED\"")
    }

    @Test
    fun 다른_키로_보낸_결제_요청은_다시_실행된다() {
        whenever(paymentCommandService.processPayment(request, BUYER_ID)).thenReturn(payment(PaymentStatus.SUCCESS))

        call("payment-key-1") { controller.processPayment(request, buyer) }
        call("payment-key-2") { controller.processPayment(request, buyer) }

        verify(paymentCommandService, times(2)).processPayment(request, BUYER_ID)
    }

    @Test
    fun 멱등성_키_없이_결제를_요청하면_결제를_실행하지_않고_거절한다() {
        val httpRequest = MockHttpServletRequest("POST", PAYMENT_URI)
        httpRequest.addHeader("X-User-Id", BUYER_ID.toString())
        RequestContextHolder.setRequestAttributes(ServletRequestAttributes(httpRequest))

        assertThatThrownBy { controller.processPayment(request, buyer) }
            .isInstanceOf(IdempotencyKeyMissingException::class.java)
        verify(paymentCommandService, never()).processPayment(any(), any())
    }

    private fun call(key: String, action: () -> Any?): Any? {
        val httpRequest = MockHttpServletRequest("POST", PAYMENT_URI)
        httpRequest.addHeader("X-User-Id", BUYER_ID.toString())
        httpRequest.addHeader("Idempotency-Key", key)
        RequestContextHolder.setRequestAttributes(ServletRequestAttributes(httpRequest))
        return action()
    }

    private fun asResponse(result: Any?): ResponseEntity<*> {
        assertThat(result).isInstanceOf(ResponseEntity::class.java)
        return result as ResponseEntity<*>
    }

    private fun assertSameReplay(first: ResponseEntity<*>, second: ResponseEntity<*>) {
        assertThat(first.statusCode.value()).isEqualTo(200)
        assertThat(second.statusCode).isEqualTo(first.statusCode)
        assertThat(objectMapper.writeValueAsString(second.body)).isEqualTo(objectMapper.writeValueAsString(first.body))
    }

    private fun payment(status: PaymentStatus): PaymentResponse {
        val now = LocalDateTime.of(2026, 11, 15, 9, 0)
        return PaymentResponse(
            id = 20L, orderId = ORDER_ID, userId = BUYER_ID, amount = BigDecimal("30000.50"),
            pointAmount = BigDecimal.ZERO, couponId = null, couponDiscountAmount = BigDecimal.ZERO,
            method = PaymentMethod.CREDIT_CARD.name, status = status,
            transactionId = if (status == PaymentStatus.SUCCESS) "MOCK_1_1000" else null,
            errorMessage = if (status == PaymentStatus.FAILED) "카드 한도 초과" else null,
            completedAt = if (status == PaymentStatus.SUCCESS) now else null,
            createdAt = now, updatedAt = now
        )
    }

    companion object {
        private const val BUYER_ID = 1L
        private const val ORDER_ID = 10L
        private const val PAYMENT_URI = "/api/v1/payments"
    }
}
