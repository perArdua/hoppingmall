package com.hoppingmall.product.inventory.service

import com.hoppingmall.product.inventory.exception.InventoryBusyException
import com.hoppingmall.product.inventory.exception.InventoryInsufficientStockException
import com.hoppingmall.product.inventory.exception.code.InventoryErrorCode
import com.hoppingmall.product.support.MySqlIntegrationTestSupport
import io.micrometer.core.instrument.MeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import java.sql.Connection
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

@DisplayName("예약 진입 상한 MySQL 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@TestPropertySource(properties = ["inventory.mode=bucket", "inventory.bucket.count=8", "inventory.semaphore.permits=2"])
class InventoryReservationGateMySqlIntegrationTest : MySqlIntegrationTestSupport() {

    @Autowired
    private lateinit var commandService: InventoryCommandService

    @Autowired
    private lateinit var meterRegistry: MeterRegistry

    @Test
    fun 허가가_가득_찬_상품의_예약은_기다리지_않고_INV005로_끝나고_허가가_반납되면_다음_예약은_성공한다() {
        val productId = 11_001L
        seed(productId, List(8) { 10 })
        val rejectedBefore = rejected()

        val holders = whileBucketsLocked(productId) {
            val pending = holdAllPermits(productId)

            assertThatThrownBy { commandService.reserveStock(productId, 1) }
                .isInstanceOfSatisfying(InventoryBusyException::class.java) {
                    assertThat(it.errorCode).isEqualTo(InventoryErrorCode.INVENTORY_BUSY)
                    assertThat(it.errorCode.code).isEqualTo("INV005")
                }
            assertThat(bucketStocks(productId)).containsOnly(10)
            assertThat(reservationCount()).isZero()
            assertThat(pending).noneMatch { it.isDone }
            pending
        }

        holders.forEach { it.get(30, TimeUnit.SECONDS) }
        assertThat(rejected() - rejectedBefore).isEqualTo(1.0)
        assertThat(bucketStocks(productId).sum()).isEqualTo(78)

        commandService.reserveStock(productId, 1)

        assertThat(bucketStocks(productId).sum()).isEqualTo(77)
        assertThat(reservationCount()).isEqualTo(3)
    }

    @Test
    fun 배치_예약에서_둘째_상품의_허가를_못_얻으면_첫째_상품의_허가가_반납되고_아무것도_남지_않는다() {
        val first = 11_101L
        val second = 11_102L
        seed(first, List(8) { 10 })
        seed(second, List(8) { 10 })

        val holders = whileBucketsLocked(second) {
            val pending = holdAllPermits(second)

            repeat(PERMITS + 1) {
                assertThatThrownBy { commandService.batchReserveStock(listOf(first to 1, second to 1)) }
                    .isInstanceOf(InventoryBusyException::class.java)
            }
            assertThat(bucketStocks(first)).containsOnly(10)
            assertThat(bucketStocks(second)).containsOnly(10)
            assertThat(reservationCount()).isZero()

            commandService.reserveStock(first, 1)

            assertThat(bucketStocks(first).sum()).isEqualTo(79)
            pending
        }

        holders.forEach { it.get(30, TimeUnit.SECONDS) }
        assertThat(bucketStocks(second).sum()).isEqualTo(78)
        assertThat(reservationCount()).isEqualTo(3)
    }

    @Test
    fun 예약이_재고_부족으로_끝나도_허가가_반납된다() {
        val productId = 11_201L
        seed(productId, List(8) { 1 })

        repeat(PERMITS + 1) {
            assertThatThrownBy { commandService.reserveStock(productId, 9) }
                .isInstanceOf(InventoryInsufficientStockException::class.java)
        }

        commandService.reserveStock(productId, 1)

        assertThat(bucketStocks(productId).sum()).isEqualTo(7)
        assertThat(reservationCount()).isEqualTo(1)
    }

    private fun <T> whileBucketsLocked(productId: Long, block: () -> T): T {
        dataSource.connection.use { holder ->
            holder.autoCommit = false
            try {
                lockBuckets(holder, productId)
                val result = block()
                holder.commit()
                return result
            } finally {
                holder.rollback()
                holder.autoCommit = true
            }
        }
    }

    private fun lockBuckets(connection: Connection, productId: Long) {
        connection.prepareStatement(
            "SELECT stock_quantity FROM inventory_buckets WHERE product_id = ? AND bucket_no = ? FOR UPDATE"
        ).use { statement ->
            (0 until 8).forEach { bucketNo ->
                statement.setLong(1, productId)
                statement.setInt(2, bucketNo)
                statement.executeQuery().use { rows -> assertThat(rows.next()).isTrue() }
            }
        }
    }

    private fun holdAllPermits(productId: Long): List<Future<String>> {
        val pending = (1..PERMITS).map { executor.submit<String> { commandService.reserveStock(productId, 1) } }
        val deadline = System.currentTimeMillis() + LOCK_WAIT_LIMIT_MS
        while (lockWaitCount("inventory_buckets") < PERMITS) {
            if (pending.any { it.isDone } || System.currentTimeMillis() > deadline) {
                throw AssertionError("허가를 쥔 예약 ${PERMITS}건의 잠금 대기가 보이지 않았다: done=${pending.map { it.isDone }}")
            }
            Thread.onSpinWait()
        }
        return pending
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

    private fun reservationCount(): Long =
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM inventory_reservations", Long::class.java)!!

    private fun rejected(): Double =
        meterRegistry.counter("inventory.reserve.rejected", "reason", "queue_full").count()

    companion object {
        private const val PERMITS = 2
        private const val LOCK_WAIT_LIMIT_MS = 15_000L
    }
}
