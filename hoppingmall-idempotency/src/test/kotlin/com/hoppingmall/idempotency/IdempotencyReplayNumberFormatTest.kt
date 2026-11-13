package com.hoppingmall.idempotency

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.hoppingmall.common.ApiResponse
import org.assertj.core.api.Assertions.assertThat
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
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@DisplayName("멱등성 재생 응답의 숫자 표기")
@DisplayNameGeneration(ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class IdempotencyReplayNumberFormatTest {

    @Mock
    private lateinit var idempotencyRecordRepository: IdempotencyRecordRepository

    @Mock
    private lateinit var redissonClient: RedissonClient

    @Mock
    private lateinit var lock: RLock

    private val objectMapper = ObjectMapper().setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)

    private val store = mutableMapOf<String, IdempotencyRecord>()

    private val controller = AmountController()

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        whenever(idempotencyRecordRepository.findByIdempotencyKey(any())).thenAnswer { store[it.getArgument(0)] }
        whenever(idempotencyRecordRepository.save(any<IdempotencyRecord>())).thenAnswer {
            val record = it.getArgument<IdempotencyRecord>(0)
            store[record.idempotencyKey] = record
            record
        }
        whenever(redissonClient.getLock(any<String>())).thenReturn(lock)
        whenever(lock.tryLock(0L, 30L, TimeUnit.SECONDS)).thenReturn(true)
        whenever(lock.isHeldByCurrentThread).thenReturn(true)

        val aspect = IdempotencyAspect(IdempotencyService(idempotencyRecordRepository), redissonClient, objectMapper)
        val proxyFactory = AspectJProxyFactory(controller)
        proxyFactory.addAspect(aspect)
        mockMvc = MockMvcBuilders.standaloneSetup(proxyFactory.getProxy<AmountController>())
            .setMessageConverters(MappingJackson2HttpMessageConverter(objectMapper))
            .build()
    }

    @AfterEach
    fun tearDown() {
        store.clear()
    }

    private fun send(amount: String, key: String): String =
        mockMvc.perform(
            post("/amounts/{amount}", amount)
                .header(IdempotencyAspect.IDEMPOTENCY_KEY_HEADER, key)
                .header("X-User-Id", "1")
        ).andReturn().response.contentAsString

    @Test
    fun 소수_둘째_자리까지_쓴_금액은_재생_응답에서도_같은_글자로_나간다() {
        val first = send("3000.00", "amount-key")
        val replayed = send("3000.00", "amount-key")

        assertThat(first).contains("\"total_amount\":3000.00")
        assertThat(replayed).isEqualTo(first)
        assertThat(controller.calls.get()).isEqualTo(1)
    }

    @Test
    fun 큰_금액은_재생_응답에서도_정밀도를_잃지_않는다() {
        val first = send("12345678901234.56", "large-amount-key")
        val replayed = send("12345678901234.56", "large-amount-key")

        assertThat(first).contains("\"total_amount\":12345678901234.56")
        assertThat(replayed).isEqualTo(first)
        assertThat(controller.calls.get()).isEqualTo(1)
    }

    @RestController
    class AmountController {

        val calls = AtomicInteger()

        @Idempotent(ttlHours = 24, lockTimeoutSeconds = 30)
        @PostMapping("/amounts/{amount}")
        fun create(@PathVariable("amount") amount: String): ResponseEntity<ApiResponse<AmountResponse>> {
            val orderId = calls.incrementAndGet().toLong()
            return ResponseEntity.ok(ApiResponse.success(AmountResponse(orderId, BigDecimal(amount), "CANCEL_REQUESTED")))
        }
    }

    data class AmountResponse(
        val orderId: Long,
        val totalAmount: BigDecimal,
        val status: String
    )
}
