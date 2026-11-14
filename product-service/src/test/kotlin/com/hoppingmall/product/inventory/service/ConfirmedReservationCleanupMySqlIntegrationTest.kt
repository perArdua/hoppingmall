package com.hoppingmall.product.inventory.service

import com.hoppingmall.common.config.InternalRestTemplateConfig
import com.hoppingmall.product.inventory.port.HttpOrderTerminalCheckAdapter
import com.hoppingmall.product.support.MySqlIntegrationTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.boot.test.web.client.MockServerRestTemplateCustomizer
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.json.JsonCompareMode
import org.springframework.test.web.client.ExpectedCount
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.ResponseCreator
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withServerError
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import java.net.SocketTimeoutException
import java.sql.Timestamp
import java.time.LocalDateTime

@DisplayName("확정 예약 정리 MySQL 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class ConfirmedReservationCleanupMySqlIntegrationTest : MySqlIntegrationTestSupport() {

    private val now: LocalDateTime = LocalDateTime.now()
    private val old: LocalDateTime = now.minusDays(61)

    private lateinit var server: MockRestServiceServer
    private lateinit var adapter: HttpOrderTerminalCheckAdapter

    @BeforeEach
    fun setUpOrderServer() {
        val mockServer = MockServerRestTemplateCustomizer()
        val tokenCustomizer = InternalRestTemplateConfig(INTERNAL_TOKEN).internalTokenRestTemplateCustomizer()
        adapter = HttpOrderTerminalCheckAdapter(ORDER_URL, RestTemplateBuilder(tokenCustomizer, mockServer))
        server = mockServer.server
    }

    @Test
    fun 끝났다고_답한_확정_예약만_지운다() {
        insert("old-a", "CONFIRMED", old)
        insert("old-b", "CONFIRMED", old)
        insert("old-unknown", "CONFIRMED", old)
        insert("young-confirmed", "CONFIRMED", now.minusDays(59))
        insert("old-cancelled", "CANCELLED", old)
        insert("old-reserved", "RESERVED", old)
        insert("old-claimed", "CLAIMED", old)
        expectCheck(listOf("old-a", "old-b", "old-unknown"), answer(listOf("old-a", "old-b")))

        val result = job().cleanUp(now)

        server.verify()
        assertThat(result.deleted).isEqualTo(2)
        assertThat(result.failed).isFalse()
        assertThat(remaining()).containsExactlyInAnyOrder(
            "old-unknown", "young-confirmed", "old-cancelled", "old-reserved", "old-claimed"
        )
    }

    @Test
    fun 물은_적_없는_id가_답에_있어도_지우지_않는다() {
        insert("old-a", "CONFIRMED", old)
        insert("young-b", "CONFIRMED", now.minusDays(1))
        expectCheck(listOf("old-a"), answer(listOf("old-a", "young-b", "not-asked")))

        val result = job().cleanUp(now)

        server.verify()
        assertThat(result.deleted).isEqualTo(1)
        assertThat(remaining()).containsExactly("young-b")
    }

    @Test
    fun order가_5xx로_답하면_아무것도_지우지_않는다() {
        insert("old-a", "CONFIRMED", old)
        insert("old-b", "CONFIRMED", old)
        expectCheck(listOf("old-a", "old-b"), withServerError())

        val result = job().cleanUp(now)

        server.verify()
        assertThat(result.deleted).isZero()
        assertThat(result.failed).isTrue()
        assertThat(remaining()).containsExactlyInAnyOrder("old-a", "old-b")
    }

    @Test
    fun order에_입구가_없어_404로_답하면_아무것도_지우지_않는다() {
        insert("old-a", "CONFIRMED", old)
        expectCheck(listOf("old-a"), withStatus(HttpStatus.NOT_FOUND))

        val result = job().cleanUp(now)

        server.verify()
        assertThat(result.deleted).isZero()
        assertThat(result.failed).isTrue()
        assertThat(remaining()).containsExactly("old-a")
    }

    @Test
    fun order가_시간_안에_답하지_않으면_아무것도_지우지_않는다() {
        insert("old-a", "CONFIRMED", old)
        expectCheck(listOf("old-a"), ResponseCreator { throw SocketTimeoutException("Read timed out") })

        val result = job().cleanUp(now)

        server.verify()
        assertThat(result.deleted).isZero()
        assertThat(result.failed).isTrue()
        assertThat(remaining()).containsExactly("old-a")
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            """{"code":"SUCCESS","message":"성공","data":{"reservation_ids":["old-a"]}}""",
            """{"code":"SUCCESS","message":"성공","data":{"terminal_reservation_ids":"old-a"}}""",
            """{"code":"SUCCESS","message":"성공","data":{"terminal_reservation_ids":[1]}}""",
            """{"code":"SUCCESS","message":"성공","data":null}""",
            """{"code":"FAIL","message":"실패","data":{"terminal_reservation_ids":["old-a"]}}""",
            """["old-a"]"""
        ]
    )
    fun 응답_형식이_약속과_다르면_아무것도_지우지_않는다(body: String) {
        insert("old-a", "CONFIRMED", old)
        expectCheck(listOf("old-a"), withSuccess(body, MediaType.APPLICATION_JSON))

        val result = job().cleanUp(now)

        server.verify()
        assertThat(result.deleted).isZero()
        assertThat(result.failed).isTrue()
        assertThat(remaining()).containsExactly("old-a")
    }

    @Test
    fun 배치_크기로_나눠_묻고_모두_지운다() {
        (1..5).forEach { insert("old-$it", "CONFIRMED", old) }
        expectCheck(listOf("old-1", "old-2"), answer(listOf("old-1", "old-2")))
        expectCheck(listOf("old-3", "old-4"), answer(listOf("old-3", "old-4")))
        expectCheck(listOf("old-5"), answer(listOf("old-5")))

        val result = job(batchSize = 2).cleanUp(now)

        server.verify()
        assertThat(result.asked).isEqualTo(5)
        assertThat(result.deleted).isEqualTo(5)
        assertThat(remaining()).isEmpty()
    }

    @Test
    fun 끝나지_않았다고_답한_배치는_남기고_다음_배치를_묻는다() {
        (1..4).forEach { insert("old-$it", "CONFIRMED", old) }
        expectCheck(listOf("old-1", "old-2"), answer(emptyList()))
        expectCheck(listOf("old-3", "old-4"), answer(listOf("old-3", "old-4")))

        val result = job(batchSize = 2).cleanUp(now)

        server.verify()
        assertThat(result.deleted).isEqualTo(2)
        assertThat(remaining()).containsExactlyInAnyOrder("old-1", "old-2")
    }

    @Test
    fun 두_번째_배치에서_order가_실패하면_첫_배치만_지우고_그_주기를_멈춘다() {
        (1..6).forEach { insert("old-$it", "CONFIRMED", old) }
        expectCheck(listOf("old-1", "old-2"), answer(listOf("old-1", "old-2")))
        expectCheck(listOf("old-3", "old-4"), withServerError())

        val result = job(batchSize = 2).cleanUp(now)

        server.verify()
        assertThat(result.deleted).isEqualTo(2)
        assertThat(result.failed).isTrue()
        assertThat(remaining()).containsExactlyInAnyOrder("old-3", "old-4", "old-5", "old-6")
    }

    @Test
    fun 묻는_사이_취소로_바뀐_예약은_지우지_않는다() {
        insert("old-a", "CONFIRMED", old)
        insert("old-b", "CONFIRMED", old)
        expectCheck(listOf("old-a", "old-b")) { request ->
            jdbcTemplate.update(
                "UPDATE inventory_reservations SET status = 'CANCELLED', updated_at = ? WHERE reservation_id = 'old-a'",
                Timestamp.valueOf(LocalDateTime.now())
            )
            answer(listOf("old-a", "old-b")).createResponse(request)
        }

        val result = job().cleanUp(now)

        server.verify()
        assertThat(result.deleted).isEqualTo(1)
        assertThat(remaining()).containsExactly("old-a")
        assertThat(statusOf("old-a")).isEqualTo("CANCELLED")
    }

    @Test
    fun 한_번에_묻는_예약은_500개를_넘지_않는다() {
        val ids = (1..501).map { "old-%04d".format(it) }
        jdbcTemplate.batchUpdate(INSERT_SQL, ids.map { arrayOf(it, PRODUCT_ID, "CONFIRMED", Timestamp.valueOf(old), Timestamp.valueOf(old), Timestamp.valueOf(old)) })
        server.expect(ExpectedCount.once(), requestTo(CHECK_URL))
            .andExpect(jsonPath("$.reservation_ids.length()").value(500))
            .andRespond(answer(emptyList()))
        server.expect(ExpectedCount.once(), requestTo(CHECK_URL))
            .andExpect(jsonPath("$.reservation_ids.length()").value(1))
            .andRespond(answer(emptyList()))

        val result = job(batchSize = 500).cleanUp(now)

        server.verify()
        assertThat(result.asked).isEqualTo(501)
        assertThat(result.deleted).isZero()
    }

    @Test
    fun 한_주기에_최대_배치_수까지만_묻는다() {
        (1..4).forEach { insert("old-$it", "CONFIRMED", old) }
        expectCheck(listOf("old-1", "old-2"), answer(listOf("old-1", "old-2")))

        val result = job(batchSize = 2, maxBatches = 1).cleanUp(now)

        server.verify()
        assertThat(result.deleted).isEqualTo(2)
        assertThat(result.failed).isFalse()
        assertThat(remaining()).containsExactlyInAnyOrder("old-3", "old-4")
    }

    @Test
    fun 지울_확정_예약이_없으면_order에_묻지_않는다() {
        insert("young-confirmed", "CONFIRMED", now.minusDays(1))
        server.expect(ExpectedCount.never(), requestTo(CHECK_URL))

        val result = job().cleanUp(now)

        server.verify()
        assertThat(result.asked).isZero()
        assertThat(result.deleted).isZero()
        assertThat(result.failed).isFalse()
    }

    private fun job(batchSize: Int = 500, maxBatches: Int = 100): ConfirmedReservationCleanupJob {
        val properties = ConfirmedReservationCleanupProperties().apply {
            this.ageDays = AGE_DAYS
            this.batchSize = batchSize
            this.maxBatches = maxBatches
        }
        return ConfirmedReservationCleanupJob(jdbcTemplate, adapter, properties)
    }

    private fun expectCheck(reservationIds: List<String>, response: ResponseCreator) {
        val body = reservationIds.joinToString(",", prefix = """{"reservation_ids":[""", postfix = "]}") { "\"$it\"" }
        server.expect(ExpectedCount.once(), requestTo(CHECK_URL))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("X-Internal-Token", INTERNAL_TOKEN))
            .andExpect(content().json(body, JsonCompareMode.STRICT))
            .andRespond(response)
    }

    private fun answer(terminalIds: List<String>): ResponseCreator {
        val ids = terminalIds.joinToString(",") { "\"$it\"" }
        return withSuccess(
            """{"code":"SUCCESS","message":"성공","data":{"terminal_reservation_ids":[$ids]}}""",
            MediaType.APPLICATION_JSON
        )
    }

    private fun insert(reservationId: String, status: String, updatedAt: LocalDateTime) {
        jdbcTemplate.update(
            INSERT_SQL,
            reservationId,
            PRODUCT_ID,
            status,
            Timestamp.valueOf(updatedAt.minusMinutes(5)),
            Timestamp.valueOf(updatedAt.minusMinutes(10)),
            Timestamp.valueOf(updatedAt)
        )
    }

    private fun remaining(): List<String> =
        jdbcTemplate.queryForList("SELECT reservation_id FROM inventory_reservations", String::class.java)

    private fun statusOf(reservationId: String): String =
        jdbcTemplate.queryForObject(
            "SELECT status FROM inventory_reservations WHERE reservation_id = ?", String::class.java, reservationId
        )!!

    companion object {
        private const val PRODUCT_ID = 97_100L
        private const val AGE_DAYS = 60L
        private const val INTERNAL_TOKEN = "test-internal-token"
        private const val ORDER_URL = "http://order-service.test"
        private const val CHECK_URL = "$ORDER_URL/internal/api/v1/orders/terminal-check"
        private val INSERT_SQL = """
            INSERT INTO inventory_reservations (reservation_id, product_id, quantity, status, expires_at, created_at, updated_at)
            VALUES (?, ?, 1, ?, ?, ?, ?)
        """.trimIndent()
    }
}
