package com.hoppingmall.payment.idempotency

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.hoppingmall.common.UserPrincipal
import com.hoppingmall.idempotency.IdempotencyAspect
import com.hoppingmall.idempotency.IdempotencyKeyMissingException
import com.hoppingmall.idempotency.IdempotencyRecord
import com.hoppingmall.idempotency.IdempotencyService
import com.hoppingmall.idempotency.IdempotentReturnTypeVerifier
import com.hoppingmall.payment.coupon.controller.CouponController
import com.hoppingmall.payment.coupon.dto.response.UserCouponResponse
import com.hoppingmall.payment.coupon.enum.DiscountType
import com.hoppingmall.payment.coupon.enum.UserCouponStatus
import com.hoppingmall.payment.coupon.service.CouponCommandService
import com.hoppingmall.payment.coupon.service.CouponQueryService
import com.hoppingmall.payment.payment.controller.PaymentController
import com.hoppingmall.payment.payment.dto.response.PaymentResponse
import com.hoppingmall.payment.payment.enum.PaymentMethod
import com.hoppingmall.payment.payment.enum.PaymentStatus
import com.hoppingmall.payment.payment.service.PaymentCommandService
import com.hoppingmall.payment.payment.service.PaymentQueryService
import com.hoppingmall.payment.point.controller.PointController
import com.hoppingmall.payment.payment.dto.request.PaymentRequest
import com.hoppingmall.payment.point.service.PointQueryService
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

@DisplayName("돈이 오가는 결제 서비스 입구의 멱등성 키 응답 재생")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class MoneyEntryIdempotencyTest {

    private val paymentCommandService: PaymentCommandService = mock()
    private val paymentQueryService: PaymentQueryService = mock()
    private val pointQueryService: PointQueryService = mock()
    private val couponCommandService: CouponCommandService = mock()
    private val couponQueryService: CouponQueryService = mock()
    private val idempotencyService: IdempotencyService = mock()
    private val redissonClient: RedissonClient = mock()
    private val objectMapper: ObjectMapper = jacksonObjectMapper().registerModule(JavaTimeModule())
    private val records = ConcurrentHashMap<String, IdempotencyRecord>()

    private val paymentController = PaymentController(paymentCommandService, paymentQueryService)
    private val pointController = PointController(pointQueryService)
    private val couponController = CouponController(couponCommandService, couponQueryService)

    private val buyer = UserPrincipal.of(BUYER_ID, "BUYER")
    private val pointPaymentRequest = PaymentRequest(
        orderId = ORDER_ID,
        amount = BigDecimal("30000.50"),
        method = PaymentMethod.CREDIT_CARD,
        pointAmount = BigDecimal("500")
    )

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
    fun 같은_키로_결제_취소를_두_번_요청하면_취소는_한_번만_실행되고_첫_응답을_받는다() {
        whenever(paymentCommandService.cancelPayment(PAYMENT_ID, BUYER_ID)).thenReturn(cancelledPayment())
        val proxy = proxyOf(paymentController)

        val first = call(CANCEL_URI, "cancel-key") { proxy.cancelPayment(PAYMENT_ID, buyer) }
        val second = call(CANCEL_URI, "cancel-key") { proxy.cancelPayment(PAYMENT_ID, buyer) }

        verify(paymentCommandService, times(1)).cancelPayment(PAYMENT_ID, BUYER_ID)
        assertSameReplay(first, second)
    }

    @Test
    fun 같은_키로_포인트를_쓴_결제를_두_번_요청하면_결제와_차감은_한_번만_실행되고_첫_응답을_받는다() {
        whenever(paymentCommandService.processPayment(pointPaymentRequest, BUYER_ID)).thenReturn(pointPayment())
        val proxy = proxyOf(paymentController)

        val first = call(PAYMENT_URI, "pay-key") { proxy.processPayment(pointPaymentRequest, buyer) }
        val second = call(PAYMENT_URI, "pay-key") { proxy.processPayment(pointPaymentRequest, buyer) }

        verify(paymentCommandService, times(1)).processPayment(pointPaymentRequest, BUYER_ID)
        assertSameReplay(first, second)
    }

    @Test
    fun 같은_키로_쿠폰_받기를_두_번_요청하면_쿠폰은_한_번만_발급되고_첫_응답을_받는다() {
        whenever(couponCommandService.issueCoupon(BUYER_ID, COUPON_ID)).thenReturn(issuedCoupon())
        val proxy = proxyOf(couponController)

        val first = call(COUPON_ISSUE_URI, "issue-key") { proxy.issueCoupon(buyer, COUPON_ID) }
        val second = call(COUPON_ISSUE_URI, "issue-key") { proxy.issueCoupon(buyer, COUPON_ID) }

        verify(couponCommandService, times(1)).issueCoupon(BUYER_ID, COUPON_ID)
        assertSameReplay(first, second)
        assertThat((second as ResponseEntity<*>).statusCode.value()).isEqualTo(201)
    }

    @Test
    fun 멱등성_키_없이_보내면_세_입구_모두_실행하지_않고_거절한다() {
        val paymentProxy = proxyOf(paymentController)
        val couponProxy = proxyOf(couponController)

        assertThatThrownBy { call(CANCEL_URI, null) { paymentProxy.cancelPayment(PAYMENT_ID, buyer) } }
            .isInstanceOf(IdempotencyKeyMissingException::class.java)
        assertThatThrownBy { call(PAYMENT_URI, null) { paymentProxy.processPayment(pointPaymentRequest, buyer) } }
            .isInstanceOf(IdempotencyKeyMissingException::class.java)
        assertThatThrownBy { call(COUPON_ISSUE_URI, null) { couponProxy.issueCoupon(buyer, COUPON_ID) } }
            .isInstanceOf(IdempotencyKeyMissingException::class.java)
        verify(paymentCommandService, never()).cancelPayment(any(), any())
        verify(paymentCommandService, never()).processPayment(any(), any())
        verify(couponCommandService, never()).issueCoupon(any(), any())
    }

    @Test
    fun 같은_키로_다른_포인트_금액의_결제를_보내면_결제하지_않고_거절한다() {
        whenever(paymentCommandService.processPayment(pointPaymentRequest, BUYER_ID)).thenReturn(pointPayment())
        val proxy = proxyOf(paymentController)
        val other = pointPaymentRequest.copy(pointAmount = BigDecimal("900"))

        call(PAYMENT_URI, "pay-key") { proxy.processPayment(pointPaymentRequest, buyer) }

        assertThatThrownBy { call(PAYMENT_URI, "pay-key") { proxy.processPayment(other, buyer) } }
            .hasFieldOrPropertyWithValue("errorCode.code", "IDEM003")
        verify(paymentCommandService, never()).processPayment(other, BUYER_ID)
    }

    @Test
    fun 멱등성을_붙인_결제_서비스_컨트롤러는_기동_때_반환형_검사를_통과한다() {
        val verifier = IdempotentReturnTypeVerifier()

        listOf(paymentController, pointController, couponController).forEach { controller ->
            assertThat(verifier.postProcessBeforeInitialization(controller, controller.javaClass.simpleName)).isSameAs(controller)
        }
    }

    private fun <T : Any> proxyOf(target: T): T =
        AspectJProxyFactory(target).apply {
            isProxyTargetClass = true
            addAspect(IdempotencyAspect(idempotencyService, redissonClient, objectMapper))
        }.getProxy()

    private fun call(uri: String, key: String?, action: () -> Any?): Any? {
        val httpRequest = MockHttpServletRequest("POST", uri)
        httpRequest.addHeader("X-User-Id", BUYER_ID.toString())
        key?.let { httpRequest.addHeader("Idempotency-Key", it) }
        RequestContextHolder.setRequestAttributes(ServletRequestAttributes(httpRequest))
        return action()
    }

    private fun assertSameReplay(first: Any?, second: Any?) {
        assertThat(first).isInstanceOf(ResponseEntity::class.java)
        assertThat(second).isInstanceOf(ResponseEntity::class.java)
        val firstResponse = first as ResponseEntity<*>
        val secondResponse = second as ResponseEntity<*>
        assertThat(secondResponse.statusCode).isEqualTo(firstResponse.statusCode)
        assertThat(objectMapper.writeValueAsString(secondResponse.body)).isEqualTo(objectMapper.writeValueAsString(firstResponse.body))
    }

    private fun cancelledPayment(): PaymentResponse {
        val now = LocalDateTime.of(2026, 11, 15, 9, 0)
        return PaymentResponse(
            id = PAYMENT_ID, orderId = ORDER_ID, userId = BUYER_ID, amount = BigDecimal("30000.50"),
            pointAmount = BigDecimal.ZERO, couponId = null, couponDiscountAmount = BigDecimal.ZERO,
            method = PaymentMethod.CREDIT_CARD.name, status = PaymentStatus.CANCELLED,
            transactionId = "MOCK_1_1000", errorMessage = null, completedAt = now,
            createdAt = now, updatedAt = now
        )
    }

    private fun pointPayment(): PaymentResponse {
        val now = LocalDateTime.of(2026, 11, 15, 9, 0)
        return PaymentResponse(
            id = PAYMENT_ID, orderId = ORDER_ID, userId = BUYER_ID, amount = BigDecimal("29500.50"),
            pointAmount = BigDecimal("500"), couponId = null, couponDiscountAmount = BigDecimal.ZERO,
            method = PaymentMethod.CREDIT_CARD.name, status = PaymentStatus.SUCCESS,
            transactionId = "MOCK_1_2000", errorMessage = null, completedAt = now,
            createdAt = now, updatedAt = now
        )
    }

    private fun issuedCoupon(): UserCouponResponse {
        val now = LocalDateTime.of(2026, 11, 15, 9, 0)
        return UserCouponResponse(
            id = 3L, couponId = COUPON_ID, couponName = "가을 할인", discountType = DiscountType.FIXED_AMOUNT,
            discountValue = BigDecimal("3000.00"), minOrderAmount = BigDecimal("10000.00"), maxDiscountAmount = null,
            status = UserCouponStatus.ISSUED, usedAt = null, validFrom = now, validTo = now.plusDays(30), createdAt = now
        )
    }

    companion object {
        private const val BUYER_ID = 1L
        private const val ORDER_ID = 10L
        private const val PAYMENT_ID = 20L
        private const val COUPON_ID = 5L
        private const val CANCEL_URI = "/api/v1/payments/20/cancel"
        private const val PAYMENT_URI = "/api/v1/payments"
        private const val COUPON_ISSUE_URI = "/api/v1/coupons/5/issue"
    }
}
