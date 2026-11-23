package com.hoppingmall.product.internal

import com.fasterxml.jackson.databind.ObjectMapper
import com.hoppingmall.product.inventory.domain.Inventory
import com.hoppingmall.product.inventory.domain.repository.InventoryRepository
import com.hoppingmall.product.support.MySqlIntegrationTestSupport
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
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.TimeUnit

@AutoConfigureMockMvc
abstract class ReusedRequestKeyRaceMySqlIntegrationTest : MySqlIntegrationTestSupport() {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    protected lateinit var inventoryRepository: InventoryRepository

    @Value("\${internal.service.token}")
    private lateinit var internalToken: String

    protected abstract fun seed(productId: Long, stockQuantity: Int)

    protected abstract fun stock(productId: Long): Int

    @Test
    fun 지운_요청_키를_다시_쓰는_같은_키_동시_요청은_500_없이_한_예약을_함께_받는다() {
        seed(PRODUCT_ID, ROUNDS * QUANTITY)
        val statuses = mutableMapOf<Int, Int>()
        val failures = mutableListOf<String>()

        repeat(ROUNDS) { round ->
            val results = simultaneously(List(CONCURRENCY) { { reserve(KEY) } })
            results.forEach { statuses.merge(it.first, 1, Int::plus) }
            val reservations = reservationIds(KEY)
            if (results.any { it.first != 200 } || results.map { it.second }.distinct().size != 1 || reservations.size != 1) {
                failures += "round=$round statuses=${results.map { it.first }} reservations=${reservations.size}"
            }
            jdbcTemplate.update("DELETE FROM inventory_reservations WHERE request_key = ?", KEY)
        }

        assertThat(failures).describedAs("rounds=%d statuses=%s", ROUNDS, statuses).isEmpty()
        assertThat(stock(PRODUCT_ID)).isEqualTo(0)
    }

    private fun reserve(requestKey: String): Pair<Int, String> {
        val response = mockMvc.post("/internal/api/v1/inventory/batch-reserve") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(listOf(mapOf("product_id" to PRODUCT_ID, "quantity" to QUANTITY)))
            header(TOKEN_HEADER, internalToken)
            header(REQUEST_KEY_HEADER, requestKey)
        }.andReturn().response
        return response.status to response.contentAsString
    }

    private fun <T> simultaneously(requests: List<() -> T>): List<T> {
        val start = CyclicBarrier(requests.size)
        return requests
            .map { request ->
                executor.submit<T> {
                    start.await(30, TimeUnit.SECONDS)
                    request()
                }
            }
            .map { it.get(60, TimeUnit.SECONDS) }
    }

    private fun reservationIds(requestKey: String): List<String> =
        jdbcTemplate.queryForList(
            "SELECT reservation_id FROM inventory_reservations WHERE request_key = ?",
            String::class.java,
            requestKey
        )

    companion object {
        const val PRODUCT_ID = 6101L
        private const val KEY = "reused-order-attempt"
        private const val QUANTITY = 1
        private const val CONCURRENCY = 5
        private const val TOKEN_HEADER = "X-Internal-Token"
        private const val REQUEST_KEY_HEADER = "X-Reservation-Request-Key"
        private val ROUNDS = System.getenv("REUSED_KEY_RACE_ROUNDS")?.toIntOrNull() ?: 200
    }
}

@DisplayName("single 모드 지운 요청 키 재사용 경합 MySQL 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class SingleReusedRequestKeyRaceMySqlIntegrationTest : ReusedRequestKeyRaceMySqlIntegrationTest() {

    override fun seed(productId: Long, stockQuantity: Int) {
        inventoryRepository.saveAndFlush(Inventory.create(productId = productId, stockQuantity = stockQuantity))
    }

    override fun stock(productId: Long): Int =
        jdbcTemplate.queryForObject("SELECT stock_quantity FROM inventories WHERE product_id = ?", Int::class.java, productId)!!
}

@DisplayName("bucket 모드 지운 요청 키 재사용 경합 MySQL 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@TestPropertySource(properties = ["inventory.mode=bucket", "inventory.bucket.count=2", "inventory.semaphore.permits=64"])
class BucketReusedRequestKeyRaceMySqlIntegrationTest : ReusedRequestKeyRaceMySqlIntegrationTest() {

    override fun seed(productId: Long, stockQuantity: Int) {
        inventoryRepository.saveAndFlush(Inventory.create(productId = productId, stockQuantity = stockQuantity))
        listOf(stockQuantity / 2, stockQuantity - stockQuantity / 2).forEachIndexed { bucketNo, stock ->
            jdbcTemplate.update(
                "INSERT INTO inventory_buckets (product_id, bucket_no, stock_quantity) VALUES (?, ?, ?)",
                productId, bucketNo, stock
            )
        }
    }

    override fun stock(productId: Long): Int =
        jdbcTemplate.queryForObject(
            "SELECT COALESCE(SUM(stock_quantity), 0) FROM inventory_buckets WHERE product_id = ?",
            Int::class.java,
            productId
        )!!
}
