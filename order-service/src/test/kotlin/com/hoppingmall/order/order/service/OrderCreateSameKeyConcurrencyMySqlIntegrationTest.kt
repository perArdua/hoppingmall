package com.hoppingmall.order.order.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.hoppingmall.common.ApiResponse
import com.hoppingmall.common.BusinessException
import com.hoppingmall.common.UserPrincipal
import com.hoppingmall.idempotency.IdempotencyAspect
import com.hoppingmall.idempotency.IdempotencyRequestContext
import com.hoppingmall.idempotency.IdempotencyService
import com.hoppingmall.idempotency.IdempotencyTxRecorder
import com.hoppingmall.order.cartItem.domain.CartItem
import com.hoppingmall.order.cartItem.domain.repository.CartItemRepository
import com.hoppingmall.order.config.JpaConfig
import com.hoppingmall.order.config.OrderMetrics
import com.hoppingmall.order.order.controller.OrderController
import com.hoppingmall.order.order.dto.request.OrderCreateRequest
import com.hoppingmall.order.order.dto.response.OrderResponse
import com.hoppingmall.order.port.InventoryCommandPort
import com.hoppingmall.order.port.PaymentCommandPort
import com.hoppingmall.order.port.ProductInfo
import com.hoppingmall.order.port.ProductQueryPort
import com.hoppingmall.order.shipping.service.ShippingCommandService
import com.hoppingmall.order.support.MySqlJpaTestSupport
import com.hoppingmall.outbox.service.TransactionalEventPublisher
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.redisson.Redisson
import org.redisson.api.RedissonClient
import org.redisson.config.Config
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.ResponseEntity
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import org.testcontainers.containers.GenericContainer
import java.math.BigDecimal
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@Import(
    JpaConfig::class,
    OrderCommandServiceImpl::class,
    ReservationFence::class,
    IdempotencyService::class,
    IdempotencyTxRecorder::class,
    OrderCreateSameKeyScenarios.TestConfig::class
)
@ImportAutoConfiguration(JacksonAutoConfiguration::class)
abstract class OrderCreateSameKeyScenarios : MySqlJpaTestSupport() {

    @Autowired
    protected lateinit var orderCommandService: OrderCommandServiceImpl

    @Autowired
    protected lateinit var idempotencyService: IdempotencyService

    @Autowired
    protected lateinit var cartItemRepository: CartItemRepository

    @Autowired
    protected lateinit var objectMapper: ObjectMapper

    @MockitoSpyBean
    protected lateinit var idempotencyTxRecorder: IdempotencyTxRecorder

    @MockitoBean
    protected lateinit var productQueryPort: ProductQueryPort

    @MockitoBean
    protected lateinit var inventoryCommandPort: InventoryCommandPort

    @MockitoBean
    protected lateinit var paymentCommandPort: PaymentCommandPort

    @MockitoBean
    protected lateinit var orderMetrics: OrderMetrics

    @MockitoBean
    protected lateinit var transactionalEventPublisher: TransactionalEventPublisher

    private val reservationsByRequestKey = ConcurrentHashMap<String, Map<Long, String>>()

    protected val createdReservations = AtomicInteger()

    private var binlogStart: Pair<String, Long>? = null

    @BeforeEach
    fun setUpScenario() {
        jdbcTemplate.update("DELETE FROM cart_items")
        jdbcTemplate.update("DELETE FROM idempotency_records")
        whenever(productQueryPort.findProductsByIds(listOf(PRODUCT_ID))).thenReturn(
            listOf(ProductInfo(id = PRODUCT_ID, name = "상품-$PRODUCT_ID", price = PRICE, sellerId = SELLER_ID))
        )
        whenever(inventoryCommandPort.batchReserveStock(any())).thenAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            val items = invocation.arguments[0] as List<Pair<Long, Int>>
            val requestKey = IdempotencyRequestContext.claimKey() ?: UUID.randomUUID().toString()
            reservationsByRequestKey.computeIfAbsent(requestKey) {
                createdReservations.incrementAndGet()
                items.associate { (productId, _) -> productId to UUID.randomUUID().toString() }
            }
        }
    }

    protected abstract fun redissonClient(): RedissonClient

    protected abstract fun configure(aspect: IdempotencyAspect)

    protected fun sendSameKeyConcurrently(requests: Int): List<String> {
        val cartItemId = cartItemRepository.save(
            CartItem.create(
                buyerId = BUYER_ID,
                productId = PRODUCT_ID,
                productName = "상품-$PRODUCT_ID",
                productPrice = PRICE,
                productImageUrl = null,
                quantity = 1
            )
        ).id!!
        val controller = controllerWithAspect()
        val key = "same-key-${UUID.randomUUID()}"
        binlogStart = binlogPosition()
        val ready = CountDownLatch(requests)
        val start = CountDownLatch(1)
        val futures = (1..requests).map {
            executor.submit(Callable {
                ready.countDown()
                start.await(10, TimeUnit.SECONDS)
                outcome(controller, key, cartItemId)
            })
        }
        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue()
        start.countDown()
        return futures.map { it.get(60, TimeUnit.SECONDS) }
    }

    protected fun writeCommitsSinceSend(): Int {
        val (file, position) = binlogStart!!
        return jdbcTemplate.queryForList("SHOW BINLOG EVENTS IN '$file' FROM $position").count { it["Event_type"] == "Xid" }
    }

    protected fun fenceDecisions(): List<String> =
        jdbcTemplate.queryForList("SELECT decision FROM reservation_fence", String::class.java)

    private fun binlogPosition(): Pair<String, Long> =
        jdbcTemplate.queryForMap("SHOW MASTER STATUS").let { it["File"] as String to (it["Position"] as Number).toLong() }

    protected fun count(table: String): Int = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM $table", Int::class.java)!!

    protected fun orderIds(): List<Long> = jdbcTemplate.queryForList("SELECT id FROM orders", Long::class.java)

    protected fun orderItemReservationIds(): List<String> =
        jdbcTemplate.queryForList("SELECT reservation_id FROM order_items", String::class.java)

    protected fun createdReservationIds(): List<String> = reservationsByRequestKey.values.flatMap { it.values }

    private fun controllerWithAspect(): OrderController {
        val aspect = IdempotencyAspect(idempotencyService, redissonClient(), objectMapper)
        configure(aspect)
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

    @TestConfiguration(proxyBeanMethods = false)
    class TestConfig {
        @Bean
        fun transactionTemplate(transactionManager: PlatformTransactionManager): TransactionTemplate =
            TransactionTemplate(transactionManager)
    }

    companion object {
        const val REQUESTS = 20
        const val BUYER_ID = 10L
        const val SELLER_ID = 5L
        const val PRODUCT_ID = 100L
        val PRICE = BigDecimal("25000.00")
    }
}

@DisplayName("같은 멱등 키 주문 동시 20건, 커밋 선점 켬 (MySQL)")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class OrderCreateSameKeyClaimByCommitMySqlIntegrationTest : OrderCreateSameKeyScenarios() {

    private val redisson: RedissonClient = mock()

    override fun redissonClient(): RedissonClient = redisson

    override fun configure(aspect: IdempotencyAspect) {
        ReflectionTestUtils.setField(aspect, "saveInTx", true)
        ReflectionTestUtils.setField(aspect, "claimByCommit", true)
    }

    @Test
    fun 모두_같은_주문으로_201을_받고_주문_예약_멱등_기록_유지_결정은_하나씩만_남으며_주문_DB_쓰기_커밋은_한_번이다() {
        val outcomes = sendSameKeyConcurrently(REQUESTS)

        val orderId = orderIds().single()
        assertThat(outcomes).hasSize(REQUESTS).containsOnly("201:$orderId")
        assertThat(count("orders")).isEqualTo(1)
        assertThat(count("idempotency_records")).isEqualTo(1)
        assertThat(createdReservations.get()).isEqualTo(1)
        assertThat(orderItemReservationIds()).containsExactlyElementsOf(createdReservationIds())
        assertThat(count("cart_items")).isZero()
        verify(inventoryCommandPort, never()).cancelReservations(any())
        verify(redisson, never()).getLock(any<String>())
        assertThat(fenceDecisions()).containsExactly(ReservationFence.LINKED)
        assertThat(writeCommitsSinceSend()).isEqualTo(1)
    }
}

@DisplayName("같은 멱등 키 주문 동시 20건, 플래그 끔 (MySQL·Redis 락)")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class OrderCreateSameKeyRedisLockMySqlIntegrationTest : OrderCreateSameKeyScenarios() {

    override fun redissonClient(): RedissonClient = redisson

    override fun configure(aspect: IdempotencyAspect) {
        ReflectionTestUtils.setField(aspect, "saveInTx", false)
        ReflectionTestUtils.setField(aspect, "claimByCommit", false)
    }

    @Test
    fun 락을_못_잡은_요청은_IDEM002를_받고_주문과_예약은_하나만_남으며_결정_펜스는_쓰지_않고_주문_DB_쓰기_커밋은_두_번이다() {
        val outcomes = sendSameKeyConcurrently(REQUESTS)

        val orderId = orderIds().single()
        assertThat(outcomes).hasSize(REQUESTS).containsOnly("201:$orderId", "409:IDEM002").contains("201:$orderId")
        assertThat(count("orders")).isEqualTo(1)
        assertThat(count("idempotency_records")).isEqualTo(1)
        assertThat(createdReservations.get()).isEqualTo(1)
        verify(inventoryCommandPort, never()).cancelReservations(any())
        assertThat(fenceDecisions()).isEmpty()
        assertThat(writeCommitsSinceSend()).isEqualTo(2)
    }

    companion object {
        private val redisson: RedissonClient by lazy { SameKeyRedis.newClient() }

        @JvmStatic
        @AfterAll
        fun shutdown() {
            runCatching { redisson.shutdown() }
        }
    }
}

@DisplayName("같은 멱등 키 주문 동시 20건, 기본값 save-in-tx 켬·커밋 선점 끔 (MySQL·Redis 락)")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class OrderCreateSameKeySaveInTxRedisLockMySqlIntegrationTest : OrderCreateSameKeyScenarios() {

    private val savedInTransaction = CopyOnWriteArrayList<Boolean>()

    override fun redissonClient(): RedissonClient = redisson

    override fun configure(aspect: IdempotencyAspect) {
        ReflectionTestUtils.setField(aspect, "saveInTx", true)
        ReflectionTestUtils.setField(aspect, "claimByCommit", false)
    }

    @BeforeEach
    fun recordInTransactionSaves() {
        doAnswer { invocation ->
            invocation.callRealMethod()
            savedInTransaction.add(IdempotencyRequestContext.current()?.savedInTransaction == true)
            null
        }.whenever(idempotencyTxRecorder).recordIfPending(any())
    }

    @Test
    fun 락을_못_잡은_요청은_IDEM002를_받고_멱등_기록은_주문_트랜잭션_안에서_한_번만_저장되며_결정_펜스는_쓰지_않는다() {
        val outcomes = sendSameKeyConcurrently(REQUESTS)

        val orderId = orderIds().single()
        assertThat(outcomes).hasSize(REQUESTS).containsOnly("201:$orderId", "409:IDEM002").contains("201:$orderId")
        assertThat(count("orders")).isEqualTo(1)
        assertThat(count("idempotency_records")).isEqualTo(1)
        assertThat(savedInTransaction).containsExactly(true)
        assertThat(createdReservations.get()).isEqualTo(1)
        assertThat(orderItemReservationIds()).containsExactlyElementsOf(createdReservationIds())
        assertThat(count("cart_items")).isZero()
        verify(inventoryCommandPort, never()).cancelReservations(any())
        assertThat(fenceDecisions()).isEmpty()
        assertThat(writeCommitsSinceSend()).isEqualTo(1)
    }

    companion object {
        private val redisson: RedissonClient by lazy { SameKeyRedis.newClient() }

        @JvmStatic
        @AfterAll
        fun shutdown() {
            runCatching { redisson.shutdown() }
        }
    }
}

private object SameKeyRedis {
    private val container: GenericContainer<*> by lazy {
        val container = GenericContainer<Nothing>("redis:7.2-alpine")
        container.withExposedPorts(6379)
        container.also { it.start() }
    }

    fun newClient(): RedissonClient =
        Redisson.create(Config().apply {
            useSingleServer().address = "redis://${container.host}:${container.getMappedPort(6379)}"
        })
}
