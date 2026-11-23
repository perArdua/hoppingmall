package com.hoppingmall.product.internal

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.fasterxml.jackson.core.type.TypeReference
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
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.post
import java.sql.Connection
import java.sql.DriverManager
import java.util.UUID
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.TimeUnit

@AutoConfigureMockMvc
abstract class ReservationRequestKeyMySqlIntegrationTest : MySqlIntegrationTestSupport() {

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

    @Test
    fun 같은_요청_키의_예약이_확정된_뒤_다시_오면_새_예약을_만들고_재고를_한_번_더_뺀다() {
        seed(PRODUCT_A, 50)
        val first = batchReserve(KEY, PRODUCT_A to 3).getValue(PRODUCT_A.toString())
        confirm(first)

        val second = batchReserve(KEY, PRODUCT_A to 3).getValue(PRODUCT_A.toString())

        assertThat(second).isNotEqualTo(first)
        assertThat(reservationStatus(first)).isEqualTo("CONFIRMED")
        assertThat(reservationStatus(second)).isEqualTo("RESERVED")
        assertThat(stock(PRODUCT_A)).isEqualTo(44)
    }

    @Test
    fun 같은_요청_키의_단건_예약이_확정된_뒤_다시_오면_새_예약을_만든다() {
        seed(PRODUCT_A, 50)
        val first = reserveOne(KEY, PRODUCT_A, 3)
        confirm(first)

        val second = reserveOne(KEY, PRODUCT_A, 3)

        assertThat(second).isNotEqualTo(first)
        assertThat(stock(PRODUCT_A)).isEqualTo(44)
    }

    @Test
    fun 같은_요청_키의_여러_상품_예약이_모두_확정된_뒤_다시_오면_상품마다_새_예약을_만든다() {
        seed(PRODUCT_A, 50)
        seed(PRODUCT_B, 20)
        val first = batchReserve(KEY, PRODUCT_A to 3, PRODUCT_B to 2)
        confirm(*first.values.toTypedArray())

        val second = batchReserve(KEY, PRODUCT_A to 3, PRODUCT_B to 2)

        assertThat(second.values).doesNotContainAnyElementsOf(first.values)
        assertThat(stock(PRODUCT_A)).isEqualTo(44)
        assertThat(stock(PRODUCT_B)).isEqualTo(16)
    }

    @Test
    fun 같은_요청_키의_예약이_일부만_확정됐으면_409로_거절하고_재고를_그대로_둔다() {
        seed(PRODUCT_A, 50)
        seed(PRODUCT_B, 20)
        val first = batchReserve(KEY, PRODUCT_A to 3, PRODUCT_B to 2)
        confirm(first.getValue(PRODUCT_A.toString()))

        batchReserveRequest(KEY, PRODUCT_A to 3, PRODUCT_B to 2).andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("INV009") }
        }

        assertThat(stock(PRODUCT_A)).isEqualTo(47)
        assertThat(stock(PRODUCT_B)).isEqualTo(18)
    }

    @Test
    fun 확정된_요청_키로_두_요청이_동시에_오면_새_예약은_하나만_만들고_둘_다_그_예약_id를_받는다() {
        seed(PRODUCT_A, 50)
        val confirmed = batchReserve(KEY, PRODUCT_A to 3).getValue(PRODUCT_A.toString())
        confirm(confirmed)

        val results = dataSource.connection.use { holder ->
            holder.autoCommit = false
            try {
                lockStock(holder, PRODUCT_A)
                val first = executor.submit<Map<String, String>> { batchReserve(KEY, PRODUCT_A to 3) }
                awaitLockWaits(1)
                val second = executor.submit<Map<String, String>> { batchReserve(KEY, PRODUCT_A to 3) }
                awaitLockWaits(1, RESERVATION_TABLE)
                holder.commit()
                listOf(first, second).map { it.get(30, TimeUnit.SECONDS).getValue(PRODUCT_A.toString()) }
            } finally {
                holder.rollback()
                holder.autoCommit = true
            }
        }

        assertThat(results[1]).isEqualTo(results[0]).isNotEqualTo(confirmed)
        assertThat(reservationIds(PRODUCT_A)).containsExactly(confirmed, results[0])
        assertThat(stock(PRODUCT_A)).isEqualTo(44)
    }

    @RepeatedTest(RACE_REPETITIONS)
    fun 마지막_한_개에_같은_요청_키_두_요청이_동시에_오면_둘_다_같은_예약_id를_받고_재고는_한_번만_빠진다() {
        seed(PRODUCT_A, 1)

        val results = simultaneously(List(2) { { reserveResult(KEY, PRODUCT_A to 1) } })

        assertSameReservation(results, PRODUCT_A)
    }

    @RepeatedTest(RACE_REPETITIONS)
    fun 남은_재고가_요청_수량과_같을_때_같은_요청_키_두_요청이_동시에_오면_둘_다_같은_예약_id를_받는다() {
        seed(PRODUCT_A, 3)

        val results = simultaneously(List(2) { { reserveResult(KEY, PRODUCT_A to 3) } })

        assertSameReservation(results, PRODUCT_A)
    }

    @RepeatedTest(RACE_REPETITIONS)
    fun 마지막_한_개에_다른_요청_키_두_요청이_동시에_오면_한쪽은_재고_부족이다() {
        seed(PRODUCT_A, 1)

        val results = simultaneously(listOf("key-1", "key-2").map { key -> { reserveResult(key, PRODUCT_A to 1) } })

        assertThat(results.map { it.first }).containsExactlyInAnyOrder(200, 400)
        assertThat(results.single { it.first == 400 }.second).contains("INV002")
        assertThat(reservationIds(PRODUCT_A)).hasSize(1)
        assertThat(stock(PRODUCT_A)).isZero()
    }

    @Test
    fun 마지막_재고에_같은_요청_키_다섯_요청이_동시에_와도_모두_같은_예약_id를_받는다() {
        seed(PRODUCT_A, 1)

        val results = simultaneously(List(5) { { reserveResult(KEY, PRODUCT_A to 1) } })

        assertSameReservation(results, PRODUCT_A)
    }

    @Test
    fun 여러_상품의_마지막_재고에_같은_요청_키_두_요청이_동시에_오면_상품마다_같은_예약_id를_받는다() {
        seed(PRODUCT_A, 3)
        seed(PRODUCT_B, 2)

        val results = whileStockLocked(PRODUCT_A, List(2) { { reserveResult(KEY, PRODUCT_A to 3, PRODUCT_B to 2) } })

        assertSameReservation(results, PRODUCT_A, PRODUCT_B)
    }

    @Test
    fun 확정된_요청_키로_마지막_재고에_두_요청이_동시에_오면_둘_다_같은_새_예약_id를_받는다() {
        seed(PRODUCT_A, 2)
        val confirmed = batchReserve(KEY, PRODUCT_A to 1).getValue(PRODUCT_A.toString())
        confirm(confirmed)

        val results = simultaneously(List(2) { { reserveResult(KEY, PRODUCT_A to 1) } })

        assertThat(results.map { it.first }).containsOnly(200)
        assertThat(results[1]).isEqualTo(results[0])
        assertThat(results[0].second).doesNotContain(confirmed)
        assertThat(reservationIds(PRODUCT_A)).hasSize(2)
        assertThat(stock(PRODUCT_A)).isZero()
    }

    @Test
    fun 마지막_재고에_같은_요청_키로_수량이_다른_두_요청이_동시에_오면_진_쪽은_409다() {
        seed(PRODUCT_A, 3)

        val results = whileStockLocked(PRODUCT_A, listOf(3, 2).map { quantity -> { reserveResult(KEY, PRODUCT_A to quantity) } })

        assertThat(results.map { it.first }).containsExactlyInAnyOrder(200, 409)
        assertThat(results.single { it.first == 409 }.second).contains("INV009")
        assertThat(reservationIds(PRODUCT_A)).hasSize(1)
    }

    @Test
    fun 같은_요청_키의_예약이_없는_품절은_재고_부족_그대로다() {
        seed(PRODUCT_A, 0)

        batchReserveRequest(KEY, PRODUCT_A to 1).andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("INV002") }
        }

        assertThat(reservationIds(PRODUCT_A)).isEmpty()
    }

    @Test
    fun 같은_요청_키의_예약이_확정된_뒤의_품절은_그_예약을_돌려주지_않고_재고_부족이다() {
        seed(PRODUCT_A, 3)
        val confirmed = batchReserve(KEY, PRODUCT_A to 3).getValue(PRODUCT_A.toString())
        confirm(confirmed)

        batchReserveRequest(KEY, PRODUCT_A to 3).andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("INV002") }
        }

        assertThat(reservationIds(PRODUCT_A)).containsExactly(confirmed)
        assertThat(reservationStatus(confirmed)).isEqualTo("CONFIRMED")
        assertThat(stock(PRODUCT_A)).isZero()
    }

    @Test
    fun 같은_요청_키의_예약이_취소된_뒤의_품절은_재고_부족이다() {
        seed(PRODUCT_A, 3)
        val cancelled = batchReserve(KEY, PRODUCT_A to 3).getValue(PRODUCT_A.toString())
        mockMvc.post("/internal/api/v1/inventory/reservations/$cancelled/cancel") {
            header(TOKEN_HEADER, internalToken)
        }.andExpect { status { isOk() } }
        batchReserve("other-key", PRODUCT_A to 3)

        batchReserveRequest(KEY, PRODUCT_A to 3).andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("INV002") }
        }

        assertThat(stock(PRODUCT_A)).isZero()
    }

    @Test
    fun 같은_요청_키_다섯_요청이_키_충돌로_끝나도_모두_같은_예약_id를_받고_ERROR_로그는_없고_재생_네_번을_센다() {
        val key = UUID.randomUUID().toString()
        seed(PRODUCT_A, 50)
        val replaysBefore = keyConflictReplays()

        val (results, errors) = capturingErrors { whileAllWaitOnStock(List(5) { { reserveResult(key, PRODUCT_A to 3) } }) }

        assertThat(results.map { it.first }).containsOnly(200)
        assertThat(results.distinct()).hasSize(1)
        assertThat(reservationIds(PRODUCT_A)).hasSize(1)
        assertThat(stock(PRODUCT_A)).isEqualTo(47)
        assertThat(errors).isEmpty()
        assertThat(keyConflictReplays() - replaysBefore).isEqualTo(4.0)
    }

    @RepeatedTest(LOG_REPETITIONS)
    fun 같은_요청_키_다섯_요청이_동시에_와도_ERROR_로그가_없고_응답은_같다() {
        val key = UUID.randomUUID().toString()
        seed(PRODUCT_A, 50)

        val (results, errors) = capturingErrors { simultaneously(List(5) { { reserveResult(key, PRODUCT_A to 3) } }) }

        assertThat(results.map { it.first }).containsOnly(200)
        assertThat(results.distinct()).hasSize(1)
        assertThat(reservationIds(PRODUCT_A)).hasSize(1)
        assertThat(stock(PRODUCT_A)).isEqualTo(47)
        assertThat(errors).isEmpty()
    }

    @Test
    fun 여러_상품의_같은_요청_키_충돌이_재생으로_끝나면_ERROR_로그가_없고_재고는_한_번씩만_빠진다() {
        val key = UUID.randomUUID().toString()
        seed(PRODUCT_A, 50)
        seed(PRODUCT_B, 20)

        val (results, errors) = capturingErrors {
            whileStockLocked(PRODUCT_A, List(2) { { reserveResult(key, PRODUCT_A to 3, PRODUCT_B to 2) } })
        }

        assertThat(results.map { it.first }).containsOnly(200)
        assertThat(results.distinct()).hasSize(1)
        assertThat(stock(PRODUCT_A)).isEqualTo(47)
        assertThat(stock(PRODUCT_B)).isEqualTo(18)
        assertThat(errors).isEmpty()
    }

    @Test
    fun 수량이_다른_같은_요청_키_충돌은_409이고_ERROR_로그가_남고_재생으로_세지_않는다() {
        val key = UUID.randomUUID().toString()
        seed(PRODUCT_A, 50)
        val replaysBefore = keyConflictReplays()

        val (results, errors) = capturingErrors {
            whileStockLocked(PRODUCT_A, listOf(3, 2).map { quantity -> { reserveResult(key, PRODUCT_A to quantity) } })
        }

        assertThat(results.map { it.first }).containsExactlyInAnyOrder(200, 409)
        assertThat(results.single { it.first == 409 }.second).contains("INV009")
        assertThat(errors).singleElement().asString().contains("Duplicate entry", "uk_reservation_request_product")
        assertThat(keyConflictReplays() - replaysBefore).isZero()
    }

    @Test
    fun 다른_제약_위반은_ERROR_로그가_남고_재생으로_세지_않는다() {
        seed(PRODUCT_A, 50)
        val replaysBefore = keyConflictReplays()
        jdbcTemplate.execute("CREATE UNIQUE INDEX $OTHER_CONSTRAINT ON inventory_reservations (product_id, quantity)")
        try {
            batchReserve("key-1", PRODUCT_A to 3)

            val (result, errors) = capturingErrors { reserveResult("key-2", PRODUCT_A to 3) }

            assertThat(result.first).isEqualTo(409)
            assertThat(result.second).contains("C003")
            assertThat(errors).singleElement().asString().contains("Duplicate entry", OTHER_CONSTRAINT)
            assertThat(stock(PRODUCT_A)).isEqualTo(47)
            assertThat(keyConflictReplays() - replaysBefore).isZero()
        } finally {
            jdbcTemplate.execute("DROP INDEX $OTHER_CONSTRAINT ON inventory_reservations")
        }
    }

    private fun keyConflictReplays(): Double =
        meterRegistry.find("inventory.reserve.replayed").tag("reason", "key_conflict").counter()?.count() ?: 0.0

    private fun <T> capturingErrors(action: () -> T): Pair<T, List<String>> {
        val root = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME) as Logger
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        root.addAppender(appender)
        try {
            val result = action()
            return result to appender.list.toList().filter { it.level == Level.ERROR }.map { it.formattedMessage }
        } finally {
            root.detachAppender(appender)
            appender.stop()
        }
    }

    private fun <T> whileAllWaitOnStock(requests: List<() -> T>): List<T> =
        DriverManager.getConnection(jdbcUrl, MySqlContainerSupport.username, MySqlContainerSupport.password).use { holder ->
            holder.autoCommit = false
            try {
                lockStock(holder, PRODUCT_A)
                val pending = requests.map { request -> executor.submit<T> { request() } }
                awaitWaitingTransactions(holder, requests.size)
                holder.commit()
                pending.map { it.get(30, TimeUnit.SECONDS) }
            } finally {
                holder.rollback()
            }
        }

    private fun awaitWaitingTransactions(observer: Connection, expected: Int) {
        val deadline = System.currentTimeMillis() + LOCK_WAIT_LIMIT_MS
        observer.prepareStatement(WAITING_TRANSACTIONS_SQL).use { statement ->
            statement.setString(1, lockedTable)
            while (System.currentTimeMillis() < deadline) {
                val waiting = statement.executeQuery().use { rows ->
                    rows.next()
                    rows.getInt(1)
                }
                if (waiting >= expected) return
                Thread.sleep(10)
            }
        }
        throw AssertionError("$lockedTable 잠금을 기다리는 트랜잭션 ${expected}개가 ${LOCK_WAIT_LIMIT_MS}ms 안에 보이지 않았다")
    }

    private fun assertSameReservation(results: List<Pair<Int, String>>, vararg productIds: Long) {
        assertThat(results.map { it.first }).containsOnly(200)
        val bodies = results.map { objectMapper.readValue(it.second, object : TypeReference<Map<String, String>>() {}) }
        assertThat(bodies.distinct()).hasSize(1)
        productIds.forEach { productId ->
            assertThat(reservationIds(productId)).containsExactly(bodies[0].getValue(productId.toString()))
            assertThat(stock(productId)).isZero()
        }
    }

    private fun reserveResult(requestKey: String, vararg items: Pair<Long, Int>): Pair<Int, String> {
        val response = batchReserveRequest(requestKey, *items).andReturn().response
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
            .map { it.get(30, TimeUnit.SECONDS) }
    }

    private fun <T> whileStockLocked(productId: Long, requests: List<() -> T>): List<T> =
        dataSource.connection.use { holder ->
            holder.autoCommit = false
            try {
                lockStock(holder, productId)
                val pending = requests.map { request -> executor.submit<T> { request() } }
                awaitLockWaits(requests.size.toLong())
                holder.commit()
                pending.map { it.get(30, TimeUnit.SECONDS) }
            } finally {
                holder.rollback()
                holder.autoCommit = true
            }
        }

    private fun confirm(vararg reservationIds: String) {
        mockMvc.post("/internal/api/v1/inventory/reservations/batch-confirm") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(reservationIds.toList())
            header(TOKEN_HEADER, internalToken)
        }.andExpect { status { isOk() } }
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

    private fun awaitLockWaits(expected: Long, table: String = lockedTable) {
        val deadline = System.currentTimeMillis() + LOCK_WAIT_LIMIT_MS
        while (System.currentTimeMillis() < deadline) {
            if (lockWaitCount(table) >= expected) return
            Thread.onSpinWait()
        }
        throw AssertionError("$table 잠금 대기 ${expected}건이 ${LOCK_WAIT_LIMIT_MS}ms 안에 보이지 않았다: ${lockWaitCount(table)}건")
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
        private const val RESERVATION_TABLE = "inventory_reservations"
        private const val RACE_REPETITIONS = 50
        private const val LOG_REPETITIONS = 20
        private const val OTHER_CONSTRAINT = "uk_test_product_quantity"
        private const val WAITING_TRANSACTIONS_SQL = """
            SELECT COUNT(DISTINCT l.ENGINE_TRANSACTION_ID) FROM performance_schema.data_locks l
            WHERE l.OBJECT_SCHEMA = DATABASE() AND l.OBJECT_NAME = ? AND l.LOCK_STATUS = 'WAITING'
        """
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
