package com.hoppingmall.product.inventory.service

import com.hoppingmall.product.inventory.exception.InventoryBudgetExceededException
import com.hoppingmall.product.support.MySqlIntegrationTestSupport
import io.micrometer.core.instrument.MeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

@DisplayName("예약 예산 MySQL 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@TestPropertySource(properties = ["inventory.mode=bucket", "inventory.bucket.count=8", "inventory.semaphore.permits=64"])
class ReserveBudgetMySqlIntegrationTest : MySqlIntegrationTestSupport() {

    @Autowired
    private lateinit var commandService: InventoryCommandService

    @Autowired
    private lateinit var meterRegistry: MeterRegistry

    @AfterEach
    fun clearDeadline() {
        ReserveDeadline.clear()
    }

    @Test
    fun 기한이_있는_예약의_만료_시각은_기한에_10초를_더한_값이다() {
        seed(PRODUCT_ID, List(8) { 10 })
        val deadline = System.currentTimeMillis() + AMPLE_BUDGET_MS
        ReserveDeadline.set(deadline)

        val reservationId = commandService.reserveStock(PRODUCT_ID, 2)

        assertThat(expiresAt(reservationId))
            .isEqualTo(LocalDateTime.ofInstant(Instant.ofEpochMilli(deadline + 10_000), ZoneId.systemDefault()))
    }

    @Test
    fun 기한이_없는_예약의_만료_시각은_생성_시각에_TTL_600초를_더한_값이다() {
        seed(PRODUCT_ID, List(8) { 10 })

        val reservationId = commandService.reserveStock(PRODUCT_ID, 2)

        assertThat(ChronoUnit.MILLIS.between(createdAt(reservationId), expiresAt(reservationId))).isEqualTo(600_000L)
    }

    @Test
    fun 기한이_임박한_채_모든_버킷이_바쁘면_기다리지_않고_INV006으로_끝나고_아무것도_남지_않는다() {
        seed(PRODUCT_ID, List(8) { 10 })
        val waitBefore = count("inventory.reserve.wait")
        val busyBefore = count("inventory.reserve.bucket_busy")

        dataSource.connection.use { holder ->
            holder.autoCommit = false
            try {
                holder.prepareStatement(
                    "SELECT stock_quantity FROM inventory_buckets WHERE product_id = ? AND bucket_no = ? FOR UPDATE"
                ).use { statement ->
                    (0 until 8).forEach { bucketNo ->
                        statement.setLong(1, PRODUCT_ID)
                        statement.setInt(2, bucketNo)
                        statement.executeQuery().use { rows -> assertThat(rows.next()).isTrue() }
                    }
                }
                ReserveDeadline.set(System.currentTimeMillis() - 1)

                assertThatThrownBy { commandService.reserveStock(PRODUCT_ID, 1) }
                    .isInstanceOfSatisfying(InventoryBudgetExceededException::class.java) {
                        assertThat(it.errorCode.code).isEqualTo("INV006")
                    }

                assertThat(lockWaitCount("inventory_buckets")).isZero()
            } finally {
                holder.rollback()
                holder.autoCommit = true
            }
        }

        assertThat(count("inventory.reserve.bucket_busy") - busyBefore).isEqualTo(8.0)
        assertThat(count("inventory.reserve.wait") - waitBefore).isEqualTo(1.0)
        assertThat(bucketStocks(PRODUCT_ID)).containsOnly(10)
        assertThat(reservationCount()).isZero()
    }

    @Test
    fun 재시작_직전에_기한이_임박하면_INV006으로_끝나고_아무것도_남지_않는다() {
        seed(PRODUCT_ID, List(8) { 1 })
        val restartBefore = count("inventory.reserve.restart")
        val fallbackBefore = count("inventory.reserve.fallback")
        ReserveDeadline.set(System.currentTimeMillis() - 1)

        assertThatThrownBy { commandService.reserveStock(PRODUCT_ID, 3) }
            .isInstanceOf(InventoryBudgetExceededException::class.java)

        assertThat(count("inventory.reserve.restart") - restartBefore).isEqualTo(1.0)
        assertThat(count("inventory.reserve.fallback") - fallbackBefore).isEqualTo(1.0)
        assertThat(bucketStocks(PRODUCT_ID)).containsOnly(1)
        assertThat(reservationCount()).isZero()
    }

    @Test
    fun 배치_예약이_커밋_직전_확인에_걸리면_모든_상품의_차감이_롤백되고_예약_행이_남지_않는다() {
        seed(PRODUCT_ID, List(8) { 10 })
        seed(OTHER_PRODUCT_ID, List(8) { 10 })
        ReserveDeadline.set(System.currentTimeMillis() - 1)

        assertThatThrownBy { commandService.batchReserveStock(listOf(PRODUCT_ID to 2, OTHER_PRODUCT_ID to 1)) }
            .isInstanceOf(InventoryBudgetExceededException::class.java)

        assertThat(bucketStocks(PRODUCT_ID)).containsOnly(10)
        assertThat(bucketStocks(OTHER_PRODUCT_ID)).containsOnly(10)
        assertThat(reservationCount()).isZero()
    }

    @Test
    fun 기한이_넉넉하면_단건과_배치_예약이_평소처럼_성공하고_보존식이_성립한다() {
        seed(PRODUCT_ID, List(8) { 10 })
        seed(OTHER_PRODUCT_ID, List(8) { 10 })
        val deadline = System.currentTimeMillis() + AMPLE_BUDGET_MS
        ReserveDeadline.set(deadline)

        val single = commandService.reserveStock(PRODUCT_ID, 3)
        val batch = commandService.batchReserveStock(listOf(PRODUCT_ID to 2, OTHER_PRODUCT_ID to 4))

        assertThat(batch.keys).containsExactly(PRODUCT_ID, OTHER_PRODUCT_ID)
        assertThat(bucketStocks(PRODUCT_ID).sum()).isEqualTo(75)
        assertThat(bucketStocks(OTHER_PRODUCT_ID).sum()).isEqualTo(76)
        assertThat(bucketStocks(PRODUCT_ID).sum() + reservedQuantity(PRODUCT_ID)).isEqualTo(80)
        assertThat(bucketStocks(OTHER_PRODUCT_ID).sum() + reservedQuantity(OTHER_PRODUCT_ID)).isEqualTo(80)
        val expected = LocalDateTime.ofInstant(Instant.ofEpochMilli(deadline + 10_000), ZoneId.systemDefault())
        assertThat(listOf(single, batch.getValue(PRODUCT_ID), batch.getValue(OTHER_PRODUCT_ID)).map { expiresAt(it) })
            .containsOnly(expected)
    }

    @Test
    fun 기한이_임박해도_바로_잡히는_버킷이_있는_단건_예약은_성공한다() {
        seed(PRODUCT_ID, List(8) { 10 })
        ReserveDeadline.set(System.currentTimeMillis() - 1)

        val reservationId = commandService.reserveStock(PRODUCT_ID, 1)

        assertThat(bucketStocks(PRODUCT_ID).sum()).isEqualTo(79)
        assertThat(reservationCount()).isEqualTo(1)
        assertThat(expiresAt(reservationId)).isNotNull()
    }

    private fun seed(productId: Long, buckets: List<Int>) {
        buckets.forEachIndexed { bucketNo, stock ->
            jdbcTemplate.update(
                "INSERT INTO inventory_buckets (product_id, bucket_no, stock_quantity) VALUES (?, ?, ?)",
                productId, bucketNo, stock
            )
        }
    }

    private fun bucketStocks(productId: Long): List<Int> =
        jdbcTemplate.queryForList(
            "SELECT stock_quantity FROM inventory_buckets WHERE product_id = ? ORDER BY bucket_no",
            Int::class.java, productId
        )

    private fun reservedQuantity(productId: Long): Int =
        jdbcTemplate.queryForObject(
            "SELECT COALESCE(SUM(quantity), 0) FROM inventory_reservations WHERE product_id = ? AND status = 'RESERVED'",
            Int::class.java, productId
        )!!

    private fun reservationCount(): Int =
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM inventory_reservations", Int::class.java)!!

    private fun expiresAt(reservationId: String): LocalDateTime =
        jdbcTemplate.queryForObject("SELECT expires_at FROM inventory_reservations WHERE reservation_id = ?", LocalDateTime::class.java, reservationId)!!

    private fun createdAt(reservationId: String): LocalDateTime =
        jdbcTemplate.queryForObject("SELECT created_at FROM inventory_reservations WHERE reservation_id = ?", LocalDateTime::class.java, reservationId)!!

    private fun count(name: String): Double = meterRegistry.counter(name).count()

    companion object {
        private const val PRODUCT_ID = 15_001L
        private const val OTHER_PRODUCT_ID = 15_002L
        private const val AMPLE_BUDGET_MS = 60_000L
    }
}
