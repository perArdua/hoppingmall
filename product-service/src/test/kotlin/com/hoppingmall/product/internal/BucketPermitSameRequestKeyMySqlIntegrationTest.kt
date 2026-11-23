package com.hoppingmall.product.internal

import com.fasterxml.jackson.databind.ObjectMapper
import com.hoppingmall.product.inventory.domain.Inventory
import com.hoppingmall.product.inventory.domain.repository.InventoryRepository
import com.hoppingmall.product.support.MySqlContainerSupport
import com.hoppingmall.product.support.MySqlIntegrationTestSupport
import io.micrometer.core.instrument.MeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import java.sql.Connection
import java.sql.DriverManager
import java.util.UUID
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

@AutoConfigureMockMvc
abstract class BucketPermitSameRequestKeyMySqlIntegrationTest : MySqlIntegrationTestSupport() {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    protected lateinit var inventoryRepository: InventoryRepository

    @Autowired
    private lateinit var meterRegistry: MeterRegistry

    @Value("\${spring.datasource.url}")
    private lateinit var jdbcUrl: String

    @Value("\${internal.service.token}")
    private lateinit var internalToken: String

    protected fun assertSameKeyStorm(concurrency: Int) {
        inventoryRepository.saveAndFlush(Inventory.create(productId = PRODUCT_ID, stockQuantity = 1))
        val statuses = mutableMapOf<Int, Int>()
        val failures = mutableListOf<String>()

        repeat(STORM_ROUNDS) { round ->
            resetBuckets(1)
            val key = UUID.randomUUID().toString()
            val results = simultaneously(List(concurrency) { { reserve(key) } })
            results.forEach { statuses.merge(it.first, 1, Int::plus) }
            val reservations = reservationIds(key)
            if (results.any { it.first != 200 } || results.distinct().size != 1 || reservations.size != 1 || stock() != 0) {
                failures += "round=$round statuses=${results.map { it.first }} reservations=${reservations.size} stock=${stock()}"
            }
        }

        assertThat(failures).describedAs("concurrency=%d statuses=%s", concurrency, statuses).isEmpty()
    }

    protected fun resetBuckets(total: Int) {
        jdbcTemplate.update("DELETE FROM inventory_buckets WHERE product_id = ?", PRODUCT_ID)
        repeat(BUCKETS) { bucketNo ->
            jdbcTemplate.update(
                "INSERT INTO inventory_buckets (product_id, bucket_no, stock_quantity) VALUES (?, ?, ?)",
                PRODUCT_ID, bucketNo, total / BUCKETS + if (bucketNo < total % BUCKETS) 1 else 0
            )
        }
    }

    protected fun <T> whileBucketsLocked(action: (Connection) -> T): T =
        DriverManager.getConnection(jdbcUrl, MySqlContainerSupport.username, MySqlContainerSupport.password).use { holder ->
            holder.autoCommit = false
            try {
                holder.prepareStatement("SELECT stock_quantity FROM inventory_buckets WHERE product_id = ? FOR UPDATE").use {
                    it.setLong(1, PRODUCT_ID)
                    it.executeQuery().close()
                }
                val result = action(holder)
                holder.commit()
                result
            } finally {
                holder.rollback()
            }
        }

    protected fun awaitBucketLockWaits(expected: Long) {
        val deadline = System.currentTimeMillis() + LOCK_WAIT_LIMIT_MS
        while (System.currentTimeMillis() < deadline) {
            if (bucketLockWaiters() >= expected) return
            Thread.sleep(5)
        }
        throw AssertionError(
            listOf(
                "$BUCKET_TABLE 잠금 대기 ${expected}건이 ${LOCK_WAIT_LIMIT_MS}ms 안에 보이지 않았다: ${bucketLockWaiters()}건",
                jdbcTemplate.queryForList("SELECT ENGINE_TRANSACTION_ID, OBJECT_NAME, LOCK_MODE, LOCK_STATUS, LOCK_DATA FROM performance_schema.data_locks"),
                jdbcTemplate.queryForList("SELECT trx_id, trx_state, trx_query FROM information_schema.innodb_trx")
            ).joinToString(" | ")
        )
    }

    protected fun bucketLockWaiters(): Long =
        jdbcTemplate.queryForObject(
            """
            SELECT COUNT(DISTINCT ENGINE_TRANSACTION_ID) FROM performance_schema.data_locks
            WHERE OBJECT_SCHEMA = DATABASE() AND OBJECT_NAME = ? AND LOCK_STATUS = 'WAITING'
            """.trimIndent(),
            Long::class.java,
            BUCKET_TABLE
        )!!

    protected fun busyReplays(): Double =
        meterRegistry.find("inventory.reserve.replayed").tag("reason", "busy").counter()?.count() ?: 0.0

    protected fun reserve(requestKey: String): Pair<Int, String> {
        val response = mockMvc.post("/internal/api/v1/inventory/batch-reserve") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(listOf(mapOf("product_id" to PRODUCT_ID, "quantity" to 1)))
            header(TOKEN_HEADER, internalToken)
            header(REQUEST_KEY_HEADER, requestKey)
        }.andReturn().response
        return response.status to response.contentAsString
    }

    protected fun <T> simultaneously(requests: List<() -> T>): List<T> {
        val start = CyclicBarrier(requests.size)
        val pending: List<Future<T>> = requests.map { request ->
            executor.submit<T> {
                start.await(30, TimeUnit.SECONDS)
                request()
            }
        }
        return pending.map { it.get(60, TimeUnit.SECONDS) }
    }

    protected fun reservationIds(requestKey: String): List<String> =
        jdbcTemplate.queryForList(
            "SELECT reservation_id FROM inventory_reservations WHERE request_key = ?",
            String::class.java,
            requestKey
        )

    protected fun reservationCount(): Int =
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM inventory_reservations", Int::class.java)!!

    protected fun stock(): Int =
        jdbcTemplate.queryForObject(
            "SELECT COALESCE(SUM(stock_quantity), 0) FROM inventory_buckets WHERE product_id = ?",
            Int::class.java,
            PRODUCT_ID
        )!!

    companion object {
        const val PRODUCT_ID = 7001L
        const val BUCKETS = 8
        const val STORM_ROUNDS = 50
        const val DIFFERENT_KEY_ROUNDS = 10
        const val TOKEN_HEADER = "X-Internal-Token"
        const val REQUEST_KEY_HEADER = "X-Reservation-Request-Key"
        const val BUCKET_TABLE = "inventory_buckets"
        const val LOCK_WAIT_LIMIT_MS = 15_000L
        const val SAME_KEY_WAIT_MS = 500L
    }
}

@DisplayName("bucket 모드 허가 8개가 찼을 때 같은 요청 키 동시 요청 MySQL 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@TestPropertySource(properties = ["inventory.mode=bucket", "inventory.bucket.count=8", "inventory.semaphore.same-key-wait-ms=500"])
class BucketPermitSameRequestKeyStormMySqlIntegrationTest : BucketPermitSameRequestKeyMySqlIntegrationTest() {

    @Test
    fun 마지막_재고에_같은_요청_키_12건이_동시에_와도_429_없이_모두_한_예약을_받는다() {
        assertSameKeyStorm(concurrency = 12)
    }

    @Test
    fun 마지막_재고에_같은_요청_키_20건이_동시에_와도_429_없이_모두_한_예약을_받는다() {
        assertSameKeyStorm(concurrency = 20)
    }

    @Test
    fun 다른_요청_키_20건이_동시에_오면_허가를_넘는_요청은_지금처럼_429다() {
        inventoryRepository.saveAndFlush(Inventory.create(productId = PRODUCT_ID, stockQuantity = 1_000))
        resetBuckets(1_000)
        val statuses = mutableMapOf<Int, Int>()

        repeat(DIFFERENT_KEY_ROUNDS) {
            simultaneously(List(20) { { reserve(UUID.randomUUID().toString()) } }).forEach { statuses.merge(it.first, 1, Int::plus) }
        }

        assertThat(statuses.keys.toList()).describedAs("statuses=%s", statuses).isSubsetOf(listOf(200, 429))
        assertThat(statuses[429] ?: 0).describedAs("statuses=%s", statuses).isPositive()
        assertThat(reservationCount()).isEqualTo(statuses[200] ?: 0)
        assertThat(stock()).isEqualTo(1_000 - (statuses[200] ?: 0))
    }
}

@DisplayName("bucket 모드 허가 초과 시 같은 요청 키 기다림 MySQL 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@TestPropertySource(
    properties = [
        "inventory.mode=bucket",
        "inventory.bucket.count=8",
        "inventory.semaphore.permits=3",
        "inventory.semaphore.same-key-wait-ms=500"
    ]
)
class BucketPermitSameRequestKeyWaitMySqlIntegrationTest : BucketPermitSameRequestKeyMySqlIntegrationTest() {

    @Test
    fun 같은_키의_앞선_예약이_커밋_전에_허가가_차면_그_예약의_커밋을_기다렸다가_같은_예약을_돌려준다() {
        inventoryRepository.saveAndFlush(Inventory.create(productId = PRODUCT_ID, stockQuantity = 100))
        resetBuckets(100)
        val key = UUID.randomUUID().toString()
        val replaysBefore = busyReplays()

        val (winner, late, others) = whileBucketsLocked { _ ->
            val winner = executor.submit<Pair<Int, String>> { reserve(key) }
            awaitBucketLockWaits(1)
            val others = List(WAIT_TEST_PERMITS - 1) { executor.submit<Pair<Int, String>> { reserve(UUID.randomUUID().toString()) } }
            awaitBucketLockWaits(WAIT_TEST_PERMITS.toLong())
            val late = executor.submit<Pair<Int, String>> { reserve(key) }
            Thread.sleep(SAME_KEY_WAIT_MS / 5)
            assertThat(late.isDone).isFalse()
            Triple(winner, late, others)
        }

        others.forEach { assertThat(it.get(30, TimeUnit.SECONDS).first).isEqualTo(200) }
        val winnerResult = winner.get(30, TimeUnit.SECONDS)
        val lateResult = late.get(30, TimeUnit.SECONDS)
        assertThat(winnerResult.first).isEqualTo(200)
        assertThat(lateResult).isEqualTo(winnerResult)
        assertThat(reservationIds(key)).hasSize(1)
        assertThat(busyReplays() - replaysBefore).isEqualTo(1.0)
    }

    @Test
    fun 다른_요청_키는_허가가_차면_기다리지_않고_바로_429다() {
        inventoryRepository.saveAndFlush(Inventory.create(productId = PRODUCT_ID, stockQuantity = 100))
        resetBuckets(100)

        val (elapsedAndStatus, holders) = whileBucketsLocked { _ ->
            val holders = List(WAIT_TEST_PERMITS) { executor.submit<Pair<Int, String>> { reserve(UUID.randomUUID().toString()) } }
            awaitBucketLockWaits(WAIT_TEST_PERMITS.toLong())
            val startedAt = System.currentTimeMillis()
            val result = reserve(UUID.randomUUID().toString())
            ((System.currentTimeMillis() - startedAt) to result) to holders
        }

        holders.forEach { assertThat(it.get(30, TimeUnit.SECONDS).first).isEqualTo(200) }

        assertThat(elapsedAndStatus.second.first).isEqualTo(429)
        assertThat(elapsedAndStatus.second.second).contains("INV005")
        assertThat(elapsedAndStatus.first).isLessThan(SAME_KEY_WAIT_MS / 2)
    }

    @Test
    fun 같은_키의_앞선_예약이_기다림_한도_안에_끝나지_않으면_429다() {
        inventoryRepository.saveAndFlush(Inventory.create(productId = PRODUCT_ID, stockQuantity = 100))
        resetBuckets(100)
        val key = UUID.randomUUID().toString()

        val (lateResult, holders) = whileBucketsLocked { _ ->
            val winner = executor.submit<Pair<Int, String>> { reserve(key) }
            awaitBucketLockWaits(1)
            val others = List(WAIT_TEST_PERMITS - 1) { executor.submit<Pair<Int, String>> { reserve(UUID.randomUUID().toString()) } }
            awaitBucketLockWaits(WAIT_TEST_PERMITS.toLong())
            val startedAt = System.currentTimeMillis()
            val result = reserve(key)
            assertThat(System.currentTimeMillis() - startedAt).isGreaterThanOrEqualTo(SAME_KEY_WAIT_MS)
            result to (others + winner)
        }

        holders.forEach { assertThat(it.get(30, TimeUnit.SECONDS).first).isEqualTo(200) }
        assertThat(reservationIds(key)).hasSize(1)
        assertThat(lateResult.first).isEqualTo(429)
        assertThat(lateResult.second).contains("INV005")
    }
}

private const val WAIT_TEST_PERMITS = 3
