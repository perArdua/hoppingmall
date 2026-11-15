package com.hoppingmall.idempotency

import com.fasterxml.jackson.databind.ObjectMapper
import com.hoppingmall.common.BusinessException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.redisson.api.RLock
import org.redisson.api.RedissonClient
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import java.math.BigDecimal
import java.util.concurrent.TimeUnit

@DisplayName("멱등성 키의 요청 본문 지문 비교")
@DisplayNameGeneration(ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class IdempotencyRequestFingerprintTest {

    @Mock
    private lateinit var idempotencyRecordRepository: IdempotencyRecordRepository

    @Mock
    private lateinit var redissonClient: RedissonClient

    @Mock
    private lateinit var lock: RLock

    private val objectMapper = ObjectMapper()

    private val store = mutableMapOf<String, IdempotencyRecord>()

    private lateinit var target: PaymentEntry

    private lateinit var proxy: PaymentEntry

    @BeforeEach
    fun setUp() {
        whenever(idempotencyRecordRepository.findByIdempotencyKey(any())).thenAnswer { store[it.getArgument(0)] }
        whenever(idempotencyRecordRepository.save(any<IdempotencyRecord>())).thenAnswer {
            val record = it.getArgument<IdempotencyRecord>(0)
            store[record.idempotencyKey] = record
            record
        }
        whenever(redissonClient.getLock(any<String>())).thenReturn(lock)
        whenever(lock.tryLock(0L, 10L, TimeUnit.SECONDS)).thenReturn(true)
        whenever(lock.isHeldByCurrentThread).thenReturn(true)

        target = PaymentEntry()
        val factory = AspectJProxyFactory(target)
        factory.isProxyTargetClass = true
        factory.addAspect(IdempotencyAspect(IdempotencyService(idempotencyRecordRepository), redissonClient, objectMapper))
        proxy = factory.getProxy()
    }

    @AfterEach
    fun tearDown() {
        RequestContextHolder.resetRequestAttributes()
    }

    @Test
    fun 같은_키에_다른_본문이_오면_처리하지_않고_422_IDEM003으로_거절한다() {
        call { proxy.pay(1L, PayBody(orderId = 10L, amount = BigDecimal("1000"))) }

        val thrown = catchThrowable { call { proxy.pay(1L, PayBody(orderId = 10L, amount = BigDecimal("2000"))) } }

        assertThat(thrown).isInstanceOf(BusinessException::class.java)
        val errorCode = (thrown as BusinessException).errorCode
        assertThat(errorCode.code).isEqualTo("IDEM003")
        assertThat(errorCode.status).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY)
        assertThat(target.calls).isEqualTo(1)
    }

    @Test
    fun 같은_키에_쿼리_값이_다르면_처리하지_않고_거절한다() {
        call(query = mapOf("couponId" to "1")) { proxy.pay(1L, PayBody(orderId = 10L, amount = BigDecimal("1000"))) }

        val thrown = catchThrowable {
            call(query = mapOf("couponId" to "2")) { proxy.pay(1L, PayBody(orderId = 10L, amount = BigDecimal("1000"))) }
        }

        assertThat((thrown as BusinessException).errorCode.code).isEqualTo("IDEM003")
        assertThat(target.calls).isEqualTo(1)
    }

    @Test
    fun 같은_키에_같은_본문이_오면_첫_응답을_재생한다() {
        val first = call { proxy.pay(1L, PayBody(orderId = 10L, amount = BigDecimal("1000"))) }

        val second = call { proxy.pay(1L, PayBody(orderId = 10L, amount = BigDecimal("1000"))) }

        assertThat(second.statusCode).isEqualTo(first.statusCode)
        assertThat(objectMapper.writeValueAsString(second.body)).isEqualTo(objectMapper.writeValueAsString(first.body))
        assertThat(target.calls).isEqualTo(1)
    }

    @Test
    fun 맵_본문의_키_순서가_달라도_같은_요청으로_보고_재생한다() {
        call { proxy.payWithMap(linkedMapOf("order_id" to 10, "amount" to 1000)) }

        val second = call { proxy.payWithMap(linkedMapOf("amount" to 1000, "order_id" to 10)) }

        assertThat(second.statusCode).isEqualTo(HttpStatus.CREATED)
        assertThat(target.calls).isEqualTo(1)
    }

    @Test
    fun 지문이_없는_옛_레코드는_본문을_비교하지_않고_재생한다() {
        call { proxy.pay(1L, PayBody(orderId = 10L, amount = BigDecimal("1000"))) }
        val saved = store.values.single()
        store[saved.idempotencyKey] = IdempotencyRecord(
            idempotencyKey = saved.idempotencyKey,
            httpMethod = saved.httpMethod,
            endpoint = saved.endpoint,
            responseStatus = saved.responseStatus,
            responseBody = saved.responseBody,
            expiresAt = saved.expiresAt
        )

        val second = call { proxy.pay(1L, PayBody(orderId = 10L, amount = BigDecimal("2000"))) }

        assertThat(second.statusCode).isEqualTo(HttpStatus.CREATED)
        assertThat(target.calls).isEqualTo(1)
    }

    private fun call(query: Map<String, String> = emptyMap(), action: () -> ResponseEntity<*>): ResponseEntity<*> {
        val request = MockHttpServletRequest("POST", "/api/v1/payments/1")
        request.addHeader(IdempotencyAspect.IDEMPOTENCY_KEY_HEADER, "pay-key")
        request.addHeader("X-User-Id", "7")
        query.forEach { (name, value) -> request.addParameter(name, value) }
        RequestContextHolder.setRequestAttributes(ServletRequestAttributes(request))
        try {
            return action()
        } finally {
            RequestContextHolder.resetRequestAttributes()
        }
    }

    data class PayBody(val orderId: Long, val amount: BigDecimal)

    open class PaymentEntry {
        var calls = 0

        @Idempotent(ttlHours = 24, lockTimeoutSeconds = 10)
        open fun pay(@PathVariable paymentId: Long, @RequestBody body: PayBody): ResponseEntity<Map<String, Any>> {
            calls++
            return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("payment_id" to paymentId, "amount" to body.amount))
        }

        @Idempotent(ttlHours = 24, lockTimeoutSeconds = 10)
        open fun payWithMap(@RequestBody body: Map<String, Any>): ResponseEntity<Map<String, Any>> {
            calls++
            return ResponseEntity.status(HttpStatus.CREATED).body(body)
        }
    }
}
