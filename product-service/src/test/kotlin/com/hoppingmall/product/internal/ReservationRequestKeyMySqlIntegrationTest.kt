package com.hoppingmall.product.internal

import com.fasterxml.jackson.core.type.TypeReference
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
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.post
import java.sql.Connection
import java.util.concurrent.TimeUnit

@AutoConfigureMockMvc
abstract class ReservationRequestKeyMySqlIntegrationTest : MySqlIntegrationTestSupport() {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    protected lateinit var inventoryRepository: InventoryRepository

    @Value("\${internal.service.token}")
    private lateinit var internalToken: String

    protected abstract val lockedTable: String

    protected abstract fun seed(productId: Long, stockQuantity: Int)

    protected abstract fun stock(productId: Long): Int

    protected abstract fun lockStock(connection: Connection, productId: Long)

    @Test
    fun 같은_요청_키로_배치_예약을_두_번_보내면_예약과_재고_감소는_한_번_분량이고_같은_예약_id를_돌려준다() {
        seed(PRODUCT_A, 50)

        val first = batchReserve(KEY, PRODUCT_A to 3)
        val second = batchReserve(KEY, PRODUCT_A to 3)

        assertThat(second).isEqualTo(first)
        assertThat(reservationIds(PRODUCT_A)).containsExactly(first.getValue(PRODUCT_A.toString()))
        assertThat(stock(PRODUCT_A)).isEqualTo(47)
    }

    @Test
    fun 같은_요청_키로_여러_상품_배치_예약을_다시_보내면_상품마다_첫_예약을_그대로_돌려준다() {
        seed(PRODUCT_A, 50)
        seed(PRODUCT_B, 20)

        val first = batchReserve(KEY, PRODUCT_A to 3, PRODUCT_B to 2)
        val second = batchReserve(KEY, PRODUCT_B to 2, PRODUCT_A to 3)

        assertThat(second).isEqualTo(first)
        assertThat(reservationIds(PRODUCT_A)).containsExactly(first.getValue(PRODUCT_A.toString()))
        assertThat(reservationIds(PRODUCT_B)).containsExactly(first.getValue(PRODUCT_B.toString()))
        assertThat(stock(PRODUCT_A)).isEqualTo(47)
        assertThat(stock(PRODUCT_B)).isEqualTo(18)
    }

    @Test
    fun 같은_요청_키의_두_요청이_동시에_오면_예약은_하나만_만들고_둘_다_같은_예약_id를_받는다() {
        seed(PRODUCT_A, 50)

        val results = dataSource.connection.use { holder ->
            holder.autoCommit = false
            try {
                lockStock(holder, PRODUCT_A)
                val pending = List(2) { executor.submit<Map<String, String>> { batchReserve(KEY, PRODUCT_A to 3) } }
                awaitLockWaits(2)
                holder.commit()
                pending.map { it.get(30, TimeUnit.SECONDS) }
            } finally {
                holder.rollback()
                holder.autoCommit = true
            }
        }

        assertThat(results[1]).isEqualTo(results[0])
        assertThat(reservationIds(PRODUCT_A)).containsExactly(results[0].getValue(PRODUCT_A.toString()))
        assertThat(stock(PRODUCT_A)).isEqualTo(47)
    }

    @Test
    fun 요청_키가_다르면_요청마다_새_예약을_만든다() {
        seed(PRODUCT_A, 50)

        val first = batchReserve("key-1", PRODUCT_A to 3)
        val second = batchReserve("key-2", PRODUCT_A to 3)

        assertThat(second).isNotEqualTo(first)
        assertThat(reservationIds(PRODUCT_A)).hasSize(2)
        assertThat(stock(PRODUCT_A)).isEqualTo(44)
    }

    @Test
    fun 요청_키가_없으면_지금처럼_요청마다_새_예약을_만든다() {
        seed(PRODUCT_A, 50)

        batchReserve(null, PRODUCT_A to 3)
        batchReserve(null, PRODUCT_A to 3)

        assertThat(reservationIds(PRODUCT_A)).hasSize(2)
        assertThat(stock(PRODUCT_A)).isEqualTo(44)
    }

    @Test
    fun 같은_요청_키로_단건_예약을_두_번_보내면_예약과_재고_감소는_한_번_분량이다() {
        seed(PRODUCT_A, 50)

        val first = reserveOne(KEY, PRODUCT_A, 3)
        val second = reserveOne(KEY, PRODUCT_A, 3)

        assertThat(second).isEqualTo(first)
        assertThat(reservationIds(PRODUCT_A)).containsExactly(first)
        assertThat(stock(PRODUCT_A)).isEqualTo(47)
    }

    @Test
    fun 같은_요청_키로_다른_수량을_보내면_409로_거절하고_새_예약을_만들지_않는다() {
        seed(PRODUCT_A, 50)
        val first = batchReserve(KEY, PRODUCT_A to 3)

        batchReserveRequest(KEY, PRODUCT_A to 4).andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("INV009") }
        }

        assertThat(reservationIds(PRODUCT_A)).containsExactly(first.getValue(PRODUCT_A.toString()))
        assertThat(stock(PRODUCT_A)).isEqualTo(47)
    }

    @Test
    fun 같은_요청_키의_예약이_취소된_뒤_다시_오면_새_예약을_만든다() {
        seed(PRODUCT_A, 50)
        val first = batchReserve(KEY, PRODUCT_A to 3).getValue(PRODUCT_A.toString())
        mockMvc.post("/internal/api/v1/inventory/reservations/$first/cancel") {
            header(TOKEN_HEADER, internalToken)
        }.andExpect { status { isOk() } }

        val second = batchReserve(KEY, PRODUCT_A to 3).getValue(PRODUCT_A.toString())

        assertThat(second).isNotEqualTo(first)
        assertThat(reservationStatus(first)).isEqualTo("CANCELLED")
        assertThat(reservationStatus(second)).isEqualTo("RESERVED")
        assertThat(stock(PRODUCT_A)).isEqualTo(47)
    }

    private fun batchReserve(requestKey: String?, vararg items: Pair<Long, Int>): Map<String, String> {
        val body = batchReserveRequest(requestKey, *items)
            .andExpect { status { isOk() } }
            .andReturn().response.contentAsString
        return objectMapper.readValue(body, object : TypeReference<Map<String, String>>() {})
    }

    private fun batchReserveRequest(requestKey: String?, vararg items: Pair<Long, Int>): ResultActionsDsl =
        mockMvc.post("/internal/api/v1/inventory/batch-reserve") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(items.map { mapOf("product_id" to it.first, "quantity" to it.second) })
            header(TOKEN_HEADER, internalToken)
            if (requestKey != null) header(REQUEST_KEY_HEADER, requestKey)
        }

    private fun reserveOne(requestKey: String, productId: Long, quantity: Int): String {
        val body = mockMvc.post("/internal/api/v1/inventory/$productId/reserve") {
            param("quantity", quantity.toString())
            header(TOKEN_HEADER, internalToken)
            header(REQUEST_KEY_HEADER, requestKey)
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString
        return objectMapper.readTree(body).get("reservation_id").asText()
    }

    private fun awaitLockWaits(expected: Long) {
        val deadline = System.currentTimeMillis() + LOCK_WAIT_LIMIT_MS
        while (System.currentTimeMillis() < deadline) {
            if (lockWaitCount(lockedTable) >= expected) return
            Thread.onSpinWait()
        }
        throw AssertionError("$lockedTable 잠금 대기 ${expected}건이 ${LOCK_WAIT_LIMIT_MS}ms 안에 보이지 않았다: ${lockWaitCount(lockedTable)}건")
    }

    private fun reservationIds(productId: Long): List<String> =
        jdbcTemplate.queryForList(
            "SELECT reservation_id FROM inventory_reservations WHERE product_id = ? ORDER BY id",
            String::class.java,
            productId
        )

    private fun reservationStatus(reservationId: String): String =
        jdbcTemplate.queryForObject(
            "SELECT status FROM inventory_reservations WHERE reservation_id = ?",
            String::class.java,
            reservationId
        )!!

    companion object {
        const val PRODUCT_A = 6001L
        const val PRODUCT_B = 6002L
        private const val KEY = "order-attempt-1"
        private const val TOKEN_HEADER = "X-Internal-Token"
        private const val REQUEST_KEY_HEADER = "X-Reservation-Request-Key"
        private const val LOCK_WAIT_LIMIT_MS = 15_000L
    }
}

@DisplayName("single 모드 예약 요청 키 MySQL 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class SingleReservationRequestKeyMySqlIntegrationTest : ReservationRequestKeyMySqlIntegrationTest() {

    override val lockedTable = "inventories"

    override fun seed(productId: Long, stockQuantity: Int) {
        inventoryRepository.saveAndFlush(Inventory.create(productId = productId, stockQuantity = stockQuantity))
    }

    override fun stock(productId: Long): Int =
        jdbcTemplate.queryForObject("SELECT stock_quantity FROM inventories WHERE product_id = ?", Int::class.java, productId)!!

    override fun lockStock(connection: Connection, productId: Long) {
        connection.prepareStatement("SELECT stock_quantity FROM inventories WHERE product_id = ? FOR UPDATE").use {
            it.setLong(1, productId)
            it.executeQuery().close()
        }
    }
}

@DisplayName("bucket 모드 예약 요청 키 MySQL 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@TestPropertySource(properties = ["inventory.mode=bucket", "inventory.bucket.count=2", "inventory.semaphore.permits=64"])
class BucketReservationRequestKeyMySqlIntegrationTest : ReservationRequestKeyMySqlIntegrationTest() {

    override val lockedTable = "inventory_buckets"

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

    override fun lockStock(connection: Connection, productId: Long) {
        connection.prepareStatement("SELECT stock_quantity FROM inventory_buckets WHERE product_id = ? FOR UPDATE").use {
            it.setLong(1, productId)
            it.executeQuery().close()
        }
    }
}
