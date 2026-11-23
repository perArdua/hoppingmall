package com.hoppingmall.idempotency

import com.fasterxml.jackson.databind.ObjectMapper
import com.hoppingmall.common.BusinessException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.redisson.Redisson
import org.redisson.api.RedissonClient
import org.redisson.config.Config
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.MySQLContainer
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@DisplayName("같은 멱등성 키에 다른 본문이 동시에 올 때 (실제 MySQL·Redis)")
@DisplayNameGeneration(ReplaceUnderscores::class)
@DataJpaTest(properties = ["spring.jpa.hibernate.ddl-auto=create-drop"])
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@ContextConfiguration(classes = [IdempotencyFingerprintConcurrencyMySqlTest.TestConfig::class])
class IdempotencyFingerprintConcurrencyMySqlTest {

    @Autowired
    private lateinit var idempotencyService: IdempotencyService

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    private val objectMapper = ObjectMapper()

    @BeforeEach
    fun clean() {
        jdbcTemplate.update("DELETE FROM idempotency_records")
    }

    @Test
    fun 같은_키에_두_가지_본문이_동시에_와도_한_번만_처리되고_다른_본문은_성공_응답을_받지_않는다() {
        val target = PointEntry()
        val proxy = proxyOf(target)
        val threads = 8
        val ready = CountDownLatch(threads)
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(threads)
        try {
            val futures = (0 until threads).map { index ->
                val amount = if (index % 2 == 0) AMOUNT_A else AMOUNT_B
                executor.submit(Callable {
                    ready.countDown()
                    start.await(10, TimeUnit.SECONDS)
                    amount to outcome { proxy.use(UseBody(amount)) }
                })
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue()
            start.countDown()
            val outcomes = futures.map { it.get(30, TimeUnit.SECONDS) }

            assertThat(target.calls.get()).isEqualTo(1)
            val winner = target.lastAmount
            outcomes.forEach { (amount, result) ->
                if (amount == winner) {
                    assertThat(result).isIn("OK:$winner", "IDEM002")
                } else {
                    assertThat(result).isIn("IDEM002", "IDEM003")
                }
            }

            val loser = if (winner == AMOUNT_A) AMOUNT_B else AMOUNT_A
            assertThat(outcome { proxy.use(UseBody(winner)) }).isEqualTo("OK:$winner")
            assertThat(outcome { proxy.use(UseBody(loser)) }).isEqualTo("IDEM003")
            assertThat(target.calls.get()).isEqualTo(1)
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM idempotency_records", Long::class.java)).isEqualTo(1L)
        } finally {
            executor.shutdownNow()
        }
    }

    private fun proxyOf(target: PointEntry): PointEntry {
        val factory = AspectJProxyFactory(target)
        factory.isProxyTargetClass = true
        factory.addAspect(IdempotencyAspect(idempotencyService, redisson, objectMapper))
        return factory.getProxy()
    }

    private fun outcome(action: () -> ResponseEntity<*>): String {
        val request = MockHttpServletRequest("POST", "/api/v1/points/use")
        request.addHeader(IdempotencyAspect.IDEMPOTENCY_KEY_HEADER, "use-key")
        request.addHeader("X-User-Id", "11")
        RequestContextHolder.setRequestAttributes(ServletRequestAttributes(request))
        return try {
            val response = action()
            @Suppress("UNCHECKED_CAST")
            "OK:" + (response.body as Map<String, Any>)["amount"]
        } catch (e: BusinessException) {
            e.errorCode.code
        } finally {
            RequestContextHolder.resetRequestAttributes()
        }
    }

    data class UseBody(val amount: Int)

    open class PointEntry {
        val calls = AtomicInteger()

        @Volatile
        var lastAmount = 0

        @Idempotent(ttlHours = 24)
        open fun use(@RequestBody body: UseBody): ResponseEntity<Map<String, Any>> {
            calls.incrementAndGet()
            lastAmount = body.amount
            return ResponseEntity.status(HttpStatus.OK).body(mapOf("amount" to body.amount))
        }
    }

    @Configuration
    @EntityScan(basePackageClasses = [IdempotencyRecord::class])
    @EnableJpaRepositories(basePackageClasses = [IdempotencyRecordRepository::class])
    @Import(IdempotencyService::class)
    class TestConfig

    companion object {
        private const val AMOUNT_A = 1000
        private const val AMOUNT_B = 2000

        private val mysql: MySQLContainer<*> by lazy {
            val container: MySQLContainer<*> = MySQLContainer("mysql:8.0.44")
            container.withUsername("root")
            container.also { it.start() }
        }

        private val redisContainer: GenericContainer<*> by lazy {
            val container = GenericContainer<Nothing>("redis:7.2-alpine")
            container.withExposedPorts(6379)
            container.also { it.start() }
        }

        private val redisson: RedissonClient by lazy {
            Redisson.create(Config().apply {
                useSingleServer().address = "redis://${redisContainer.host}:${redisContainer.getMappedPort(6379)}"
            })
        }

        @JvmStatic
        @DynamicPropertySource
        fun mysqlProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { mysql.jdbcUrl }
            registry.add("spring.datasource.username") { mysql.username }
            registry.add("spring.datasource.password") { mysql.password }
            registry.add("spring.datasource.driver-class-name") { "com.mysql.cj.jdbc.Driver" }
            registry.add("spring.jpa.database-platform") { "org.hibernate.dialect.MySQLDialect" }
            registry.add("spring.datasource.hikari.maximum-pool-size") { 10 }
            registry.add("spring.datasource.hikari.minimum-idle") { 0 }
        }

        @JvmStatic
        @AfterAll
        fun shutdown() {
            runCatching { redisson.shutdown() }
        }
    }
}
