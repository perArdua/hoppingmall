package com.hoppingmall.order.order.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.hoppingmall.common.ApiResponse
import com.hoppingmall.common.BusinessException
import com.hoppingmall.common.UserPrincipal
import com.hoppingmall.common.event.KafkaEventMapper
import com.hoppingmall.idempotency.IdempotencyAspect
import com.hoppingmall.idempotency.IdempotencyService
import com.hoppingmall.idempotency.IdempotencyTxRecorder
import com.hoppingmall.order.cartItem.domain.CartItem
import com.hoppingmall.order.cartItem.domain.repository.CartItemRepository
import com.hoppingmall.order.config.JpaConfig
import com.hoppingmall.order.config.OrderMetrics
import com.hoppingmall.order.metrics.SagaCompensationMetrics
import com.hoppingmall.order.order.controller.OrderController
import com.hoppingmall.order.order.dto.request.OrderCreateRequest
import com.hoppingmall.order.order.dto.response.OrderResponse
import com.hoppingmall.order.port.HttpInventoryCommandAdapter
import com.hoppingmall.order.port.PaymentCommandPort
import com.hoppingmall.order.port.ProductInfo
import com.hoppingmall.order.port.ProductQueryPort
import com.hoppingmall.order.shipping.service.ShippingCommandService
import com.hoppingmall.order.support.MySqlJpaTestSupport
import com.hoppingmall.outbox.service.TransactionalEventPublisher
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.redisson.api.RLock
import org.redisson.api.RedissonClient
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.web.client.MockServerRestTemplateCustomizer
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.client.ClientHttpResponse
import org.springframework.mock.http.client.MockClientHttpRequest
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.test.web.client.ExpectedCount
import org.springframework.test.web.client.ResponseCreator
import org.springframework.test.web.client.UnorderedRequestExpectationManager
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import java.math.BigDecimal
import java.net.SocketTimeoutException
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@DisplayName("확정된 예약과 같은 요청 키로 다시 온 주문 (MySQL)")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@Import(
    JpaConfig::class,
    OrderCommandServiceImpl::class,
    OrderSagaConsumer::class,
    ReservationFence::class,
    IdempotencyService::class,
    IdempotencyTxRecorder::class,
    HttpInventoryCommandAdapter::class,
    ConfirmedReservationReplayMySqlIntegrationTest.TestConfig::class
)
@ImportAutoConfiguration(JacksonAutoConfiguration::class)
class ConfirmedReservationReplayMySqlIntegrationTest : MySqlJpaTestSupport() {

    @Autowired
    private lateinit var orderCommandService: OrderCommandServiceImpl

    @Autowired
    private lateinit var orderSagaConsumer: OrderSagaConsumer

    @Autowired
    private lateinit var idempotencyService: IdempotencyService

    @Autowired
    private lateinit var cartItemRepository: CartItemRepository

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var productServer: MockServerRestTemplateCustomizer

    @MockitoBean
    private lateinit var productQueryPort: ProductQueryPort

    @MockitoBean
    private lateinit var paymentCommandPort: PaymentCommandPort

    @MockitoBean
    private lateinit var orderMetrics: OrderMetrics

    @MockitoBean
    private lateinit var sagaCompensationMetrics: SagaCompensationMetrics

    @MockitoBean
    private lateinit var transactionalEventPublisher: TransactionalEventPublisher

    private lateinit var product: FakeProductInventory

    @BeforeEach
    fun setUpScenario() {
        jdbcTemplate.update("DELETE FROM cart_items")
        jdbcTemplate.update("DELETE FROM idempotency_records")
        jdbcTemplate.update("DELETE FROM saga_event_log_steps")
        jdbcTemplate.update("DELETE FROM saga_event_logs")
        whenever(productQueryPort.findProductsByIds(listOf(PRODUCT_ID))).thenReturn(
            listOf(ProductInfo(id = PRODUCT_ID, name = "상품-$PRODUCT_ID", price = PRICE, sellerId = SELLER_ID))
        )
        product = FakeProductInventory(objectMapper, mapOf(PRODUCT_ID to INITIAL_STOCK))
        productServer.server.reset()
        product.bind(productServer)
    }

    @Test
    fun 커밋_선점_켬_멱등_기록이_정리된_뒤_같은_키로_다시_주문하면_새_예약으로_재고를_한번_더_뺀다() {
        val result = runScenario(claimByCommit = true)

        assertThat(result.firstOutcome).startsWith("201:")
        assertThat(result.secondOutcome).startsWith("201:")
        assertThat(result.orderCount).isEqualTo(2)
        assertThat(result.secondReservation).isNotEqualTo(result.firstReservation)
        assertThat(product.decrements).isEqualTo(2)
        assertThat(product.stock(PRODUCT_ID)).isEqualTo(INITIAL_STOCK - 2 * QUANTITY)
        assertThat(result.firstStatus).isEqualTo("PAID")
        assertThat(result.secondStatus).isEqualTo("PAID")
        assertThat(result.secondConfirmResults).containsExactly(true)
    }

    @Test
    fun 이긴_주문이_확정된_뒤에_예약을_요청한_늦은_요청은_새_예약만_취소하고_이긴_응답을_돌려받는다() {
        val lateArrived = CountDownLatch(1)
        val releaseLate = CountDownLatch(1)
        val first = AtomicBoolean(true)
        product.onBatchReserve = { handle ->
            if (first.compareAndSet(true, false)) {
                lateArrived.countDown()
                check(releaseLate.await(WAIT_SECONDS, TimeUnit.SECONDS))
            }
            handle()
        }

        assertLateRequestCancelsOnlyItsOwnReservation(lateArrived, releaseLate)
    }

    @Test
    fun 응답을_잃고_이긴_주문이_확정된_뒤에_다시_보낸_요청은_새_예약만_취소하고_이긴_응답을_돌려받는다() {
        val lateArrived = CountDownLatch(1)
        val releaseLate = CountDownLatch(1)
        val first = AtomicBoolean(true)
        product.onBatchReserve = { handle ->
            if (first.compareAndSet(true, false)) {
                handle()
                lateArrived.countDown()
                check(releaseLate.await(WAIT_SECONDS, TimeUnit.SECONDS))
                throw SocketTimeoutException("예약 응답 유실")
            }
            handle()
        }

        assertLateRequestCancelsOnlyItsOwnReservation(lateArrived, releaseLate)
    }

    @Test
    fun 커밋_선점_끔_멱등_기록이_정리된_뒤_같은_키로_다시_주문하면_새_예약으로_재고를_한번_더_뺀다() {
        val result = runScenario(claimByCommit = false)

        assertThat(result.secondOutcome).startsWith("201:")
        assertThat(result.orderCount).isEqualTo(2)
        assertThat(result.secondReservation).isNotEqualTo(result.firstReservation)
        assertThat(product.decrements).isEqualTo(2)
        assertThat(product.stock(PRODUCT_ID)).isEqualTo(INITIAL_STOCK - 2 * QUANTITY)
        assertThat(result.secondStatus).isEqualTo("PAID")
    }

    @Test
    fun 커밋_선점_켬_멱등_기록이_만료됐지만_아직_지워지지_않았으면_두번째_주문은_만들어지지_않는다() {
        val result = runScenario(claimByCommit = true, deleteRecord = false)

        assertThat(result.secondOutcome).doesNotStartWith("201:")
        assertThat(result.orderCount).isEqualTo(1)
        assertThat(product.decrements).isEqualTo(2)
        assertThat(product.statuses().values).containsExactlyInAnyOrder("CONFIRMED", "CANCELLED")
        assertThat(product.stock(PRODUCT_ID)).isEqualTo(INITIAL_STOCK - QUANTITY)
    }

    private fun assertLateRequestCancelsOnlyItsOwnReservation(lateArrived: CountDownLatch, releaseLate: CountDownLatch) {
        val controller = controller(claimByCommit = true)
        val key = "late-key-${UUID.randomUUID()}"
        val cartItemId = saveCartItem()

        val late = executor.submit(Callable { outcome(controller, key, cartItemId) })
        assertThat(lateArrived.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue()
        val winnerOutcome = outcome(controller, key, cartItemId)
        val orderId = orderIds().single()
        orderSagaConsumer.handlePaymentCompleted(paymentCompletedMessage(orderId, "txn-winner"))
        releaseLate.countDown()
        val lateOutcome = late.get(WAIT_SECONDS, TimeUnit.SECONDS)

        val winnerReservation = reservationOf(orderId)!!
        val statuses = product.statuses()
        assertThat(winnerOutcome).isEqualTo("201:$orderId")
        assertThat(lateOutcome).isEqualTo(winnerOutcome)
        assertThat(orderIds()).containsExactly(orderId)
        assertThat(statuses[winnerReservation]).isEqualTo("CONFIRMED")
        assertThat((statuses - winnerReservation).values).containsExactly("CANCELLED")
        assertThat(product.stock(PRODUCT_ID)).isEqualTo(INITIAL_STOCK - QUANTITY)
    }

    private fun runScenario(claimByCommit: Boolean, deleteRecord: Boolean = true): ScenarioResult {
        val controller = controller(claimByCommit)
        val key = "replay-key-${UUID.randomUUID()}"

        val firstOutcome = outcome(controller, key, saveCartItem())
        val firstOrderId = orderIds().single()
        orderSagaConsumer.handlePaymentCompleted(paymentCompletedMessage(firstOrderId, "txn-first"))
        val firstConfirmResults = product.confirmResults.toList()

        jdbcTemplate.update("UPDATE idempotency_records SET expires_at = ?", LocalDateTime.now().minusMinutes(1))
        if (deleteRecord) idempotencyService.cleanupExpiredRecords()

        val secondOutcome = outcome(controller, key, saveCartItem())
        val orders = orderIds()
        val secondOrderId = orders.firstOrNull { it != firstOrderId }
        if (secondOrderId != null) {
            orderSagaConsumer.handlePaymentCompleted(paymentCompletedMessage(secondOrderId, "txn-second"))
        }
        val secondConfirmResults = product.confirmResults.drop(firstConfirmResults.size)

        return ScenarioResult(
            firstOutcome = firstOutcome,
            secondOutcome = secondOutcome,
            orderCount = orders.size,
            firstReservation = reservationOf(firstOrderId),
            secondReservation = secondOrderId?.let { reservationOf(it) },
            firstStatus = statusOf(firstOrderId),
            secondStatus = secondOrderId?.let { statusOf(it) },
            secondConfirmResults = secondConfirmResults
        )
    }

    private fun controller(claimByCommit: Boolean): OrderController {
        val lock = mock<RLock>()
        whenever(lock.tryLock(any(), any(), any<TimeUnit>())).thenReturn(true)
        whenever(lock.isHeldByCurrentThread).thenReturn(true)
        val redisson = mock<RedissonClient>()
        whenever(redisson.getLock(anyOrNull<String>())).thenReturn(lock)
        val aspect = IdempotencyAspect(idempotencyService, redisson, objectMapper)
        ReflectionTestUtils.setField(aspect, "saveInTx", true)
        ReflectionTestUtils.setField(aspect, "claimByCommit", claimByCommit)
        val factory = AspectJProxyFactory(OrderController(orderCommandService, mock(), mock<ShippingCommandService>()))
        factory.isProxyTargetClass = true
        factory.addAspect(aspect)
        return factory.getProxy()
    }

    private fun outcome(controller: OrderController, key: String, cartItemId: Long): String {
        val request = MockHttpServletRequest("POST", "/api/v1/orders")
        request.addHeader(IdempotencyAspect.IDEMPOTENCY_KEY_HEADER, key)
        request.addHeader("X-User-Id", BUYER_ID.toString())
        RequestContextHolder.setRequestAttributes(ServletRequestAttributes(request))
        return try {
            val response: ResponseEntity<*> = controller.createOrder(
                UserPrincipal.of(BUYER_ID, "BUYER"),
                OrderCreateRequest(cartItemIds = listOf(cartItemId))
            )
            "${response.statusCode.value()}:${orderId(response.body)}"
        } catch (e: BusinessException) {
            "${e.errorCode.status.value()}:${e.errorCode.code}"
        } catch (e: Exception) {
            "500:${e.javaClass.simpleName}"
        } finally {
            RequestContextHolder.resetRequestAttributes()
        }
    }

    private fun orderId(body: Any?): Long? = when (body) {
        is ApiResponse<*> -> (body.data as OrderResponse).id
        is Map<*, *> -> ((body["data"] as Map<*, *>)["id"] as Number).toLong()
        else -> null
    }

    private fun saveCartItem(): Long =
        cartItemRepository.save(
            CartItem.create(
                buyerId = BUYER_ID,
                productId = PRODUCT_ID,
                productName = "상품-$PRODUCT_ID",
                productPrice = PRICE,
                productImageUrl = null,
                quantity = QUANTITY
            )
        ).id!!

    private fun paymentCompletedMessage(orderId: Long, transactionId: String): String =
        """{"paymentId":${900 + orderId},"orderId":$orderId,"userId":$BUYER_ID,"amount":50000,"pointAmount":0,"transactionId":"$transactionId"}"""

    private fun orderIds(): List<Long> = jdbcTemplate.queryForList("SELECT id FROM orders ORDER BY id", Long::class.java)

    private fun reservationOf(orderId: Long): String? =
        jdbcTemplate.queryForList("SELECT reservation_id FROM order_items WHERE order_id = ?", String::class.java, orderId).singleOrNull()

    private fun statusOf(orderId: Long): String =
        jdbcTemplate.queryForObject("SELECT status FROM orders WHERE id = ?", String::class.java, orderId)!!

    private data class ScenarioResult(
        val firstOutcome: String,
        val secondOutcome: String,
        val orderCount: Int,
        val firstReservation: String?,
        val secondReservation: String?,
        val firstStatus: String,
        val secondStatus: String?,
        val secondConfirmResults: List<Boolean>
    )

    class FakeProductInventory(private val objectMapper: ObjectMapper, initialStock: Map<Long, Int>) {

        private data class Reservation(val id: String, var requestKey: String?, val productId: Long, val quantity: Int, var status: String)

        private val stock = initialStock.toMutableMap()
        private val reservations = mutableListOf<Reservation>()
        var onBatchReserve: (() -> ClientHttpResponse) -> ClientHttpResponse = { handle -> handle() }
        val confirmResults = mutableListOf<Boolean>()
        var decrements = 0
            private set

        fun stock(productId: Long): Int = stock.getValue(productId)

        @Synchronized
        fun statuses(): Map<String, String> = reservations.associate { it.id to it.status }

        fun bind(customizer: MockServerRestTemplateCustomizer) {
            val server = customizer.server
            server.expect(ExpectedCount.manyTimes(), requestTo("$PRODUCT_URL/internal/api/v1/inventory/batch-reserve"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(ResponseCreator { request -> onBatchReserve { batchReserve(request as MockClientHttpRequest).createResponse(request) } })
            server.expect(ExpectedCount.manyTimes(), requestTo("$PRODUCT_URL/internal/api/v1/inventory/reservations/batch-confirm"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(ResponseCreator { request -> confirm(request as MockClientHttpRequest).createResponse(request) })
            server.expect(ExpectedCount.manyTimes(), requestTo("$PRODUCT_URL/internal/api/v1/inventory/reservations/batch-cancel"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(ResponseCreator { request -> cancel(request as MockClientHttpRequest).createResponse(request) })
        }

        @Synchronized
        private fun batchReserve(request: MockClientHttpRequest): ResponseCreator {
            val requestKey = request.headers.getFirst(HttpInventoryCommandAdapter.REQUEST_KEY_HEADER)!!
            val items: List<Map<String, Number>> = objectMapper.readValue(request.bodyAsString)
            val requested = items.associate { it.getValue("productId").toLong() to it.getValue("quantity").toInt() }
            val existing = reservations.filter { it.requestKey == requestKey }
            if (existing.isEmpty()) return json(reserve(requested, requestKey))
            if (existing.all { it.status in CLOSED }) {
                existing.forEach { it.requestKey = null }
                return json(reserve(requested, requestKey))
            }
            val sameRequest = existing.size == requested.size && existing.all { requested[it.productId] == it.quantity }
            if (!sameRequest || existing.any { it.status !in REPLAYABLE }) return withStatus(HttpStatus.CONFLICT)
            return json(existing.associate { it.productId to it.id })
        }

        private fun reserve(requested: Map<Long, Int>, requestKey: String): Map<Long, String> =
            requested.toSortedMap().mapValues { (productId, quantity) ->
                check(stock.getValue(productId) >= quantity)
                stock[productId] = stock.getValue(productId) - quantity
                decrements++
                Reservation(UUID.randomUUID().toString(), requestKey, productId, quantity, "RESERVED").also { reservations.add(it) }.id
            }

        @Synchronized
        private fun confirm(request: MockClientHttpRequest): ResponseCreator {
            val ids: List<String> = objectMapper.readValue(request.bodyAsString)
            val rows = reservations.filter { it.id in ids }
            rows.filter { it.status in CONFIRMABLE }.forEach { it.status = "CONFIRMED" }
            val confirmed = rows.size == ids.size && rows.all { it.status == "CONFIRMED" }
            confirmResults.add(confirmed)
            return json(mapOf("confirmed" to confirmed))
        }

        @Synchronized
        private fun cancel(request: MockClientHttpRequest): ResponseCreator {
            val ids: List<String> = objectMapper.readValue(request.bodyAsString)
            reservations.filter { it.id in ids && it.status in CONFIRMABLE }.forEach {
                it.status = "CANCELLED"
                stock[it.productId] = stock.getValue(it.productId) + it.quantity
            }
            return withSuccess()
        }

        private fun json(body: Any): ResponseCreator = withSuccess(objectMapper.writeValueAsString(body), MediaType.APPLICATION_JSON)

        companion object {
            private const val PRODUCT_URL = "http://localhost:8083"
            private val CONFIRMABLE = setOf("RESERVED", "CLAIMED")
            private val REPLAYABLE = setOf("RESERVED", "CLAIMED")
            private val CLOSED = setOf("CONFIRMED", "CANCELLED", "EXPIRED")
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    class TestConfig {
        @Bean
        fun mockServerRestTemplateCustomizer(): MockServerRestTemplateCustomizer =
            MockServerRestTemplateCustomizer(UnorderedRequestExpectationManager::class.java)

        @Bean
        fun restTemplateBuilder(customizer: MockServerRestTemplateCustomizer): RestTemplateBuilder =
            RestTemplateBuilder(customizer)

        @Bean
        fun kafkaEventMapper(objectMapper: ObjectMapper): KafkaEventMapper = KafkaEventMapper(objectMapper)
    }

    companion object {
        private const val BUYER_ID = 10L
        private const val SELLER_ID = 5L
        private const val PRODUCT_ID = 100L
        private const val QUANTITY = 2
        private const val INITIAL_STOCK = 10
        private const val WAIT_SECONDS = 30L
        private val PRICE = BigDecimal("25000.00")
    }
}
