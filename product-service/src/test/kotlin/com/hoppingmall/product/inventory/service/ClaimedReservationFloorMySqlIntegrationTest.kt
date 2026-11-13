package com.hoppingmall.product.inventory.service

import com.hoppingmall.product.inventory.domain.repository.InventoryReservationRepository
import com.hoppingmall.product.inventory.exception.ReservationConfirmFailedException
import com.hoppingmall.product.support.MySqlIntegrationTestSupport
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.PlatformTransactionManager
import java.time.LocalDateTime

@DisplayName("선점 기한이 지난 CLAIMED 예약 바닥 만료 MySQL 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@TestPropertySource(properties = ["inventory.mode=bucket", "inventory.bucket.count=8", "inventory.semaphore.permits=64"])
class ClaimedReservationFloorMySqlIntegrationTest : MySqlIntegrationTestSupport() {

    @Autowired
    private lateinit var commandService: InventoryCommandService

    @Autowired
    private lateinit var reservationRepository: InventoryReservationRepository

    @Autowired
    private lateinit var bucketStockService: BucketStockService

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    private val port = FakeLinkPort()

    @Test
    fun 선점_기한이_지난_CLAIMED_예약은_EXPIRED가_되고_버킷_재고는_한_번만_돌아온다() {
        seed(PRODUCT_ID)
        val reservationId = claimed(PRODUCT_ID, 3)
        passClaimDeadline(reservationId)

        job().runCycle(LocalDateTime.now())
        job().runCycle(LocalDateTime.now())

        assertThat(status(reservationId)).isEqualTo("EXPIRED")
        assertThat(bucketSum(PRODUCT_ID)).isEqualTo(INITIAL)
        assertConserved(PRODUCT_ID)
    }

    @Test
    fun 선점_기한이_지나지_않은_CLAIMED_예약은_그대로_남는다() {
        seed(PRODUCT_ID)
        val reservationId = claimed(PRODUCT_ID, 3)

        job().runCycle(LocalDateTime.now())

        assertThat(status(reservationId)).isEqualTo("CLAIMED")
        assertThat(bucketSum(PRODUCT_ID)).isEqualTo(INITIAL - 3)
        assertConserved(PRODUCT_ID)
    }

    @Test
    fun 여러_상품의_선점_기한이_지난_CLAIMED_예약을_상품마다_복원한다() {
        seed(PRODUCT_ID)
        seed(OTHER_PRODUCT_ID)
        val first = claimed(PRODUCT_ID, 3)
        val second = claimed(OTHER_PRODUCT_ID, 2)
        val kept = claimed(PRODUCT_ID, 1)
        passClaimDeadline(first, second)

        job().runCycle(LocalDateTime.now())

        assertThat(status(first)).isEqualTo("EXPIRED")
        assertThat(status(second)).isEqualTo("EXPIRED")
        assertThat(status(kept)).isEqualTo("CLAIMED")
        assertThat(bucketSum(PRODUCT_ID)).isEqualTo(INITIAL - 1)
        assertThat(bucketSum(OTHER_PRODUCT_ID)).isEqualTo(INITIAL)
        assertConserved(PRODUCT_ID)
        assertConserved(OTHER_PRODUCT_ID)
    }

    @Test
    fun 연결_조회가_실패해도_선점_기한이_지난_CLAIMED_예약은_만료된다() {
        seed(PRODUCT_ID)
        val reservationId = claimed(PRODUCT_ID, 3)
        passClaimDeadline(reservationId)
        port.failure = IllegalStateException("연결 조회 실패")

        job().runCycle(LocalDateTime.now())

        assertThat(status(reservationId)).isEqualTo("EXPIRED")
        assertThat(bucketSum(PRODUCT_ID)).isEqualTo(INITIAL)
        assertConserved(PRODUCT_ID)
    }

    @Test
    fun 선점_기한이_지나_만료된_예약은_뒤늦은_확정이_실패하고_재고는_복원된_채로_남는다() {
        seed(PRODUCT_ID)
        val reservationId = claimed(PRODUCT_ID, 3)
        passClaimDeadline(reservationId)

        job().runCycle(LocalDateTime.now())

        assertThatThrownBy { commandService.confirmReservations(listOf(reservationId)) }
            .isInstanceOf(ReservationConfirmFailedException::class.java)
        assertThat(status(reservationId)).isEqualTo("EXPIRED")
        assertThat(bucketSum(PRODUCT_ID)).isEqualTo(INITIAL)
    }

    @Test
    fun 바닥_만료가_잡으려는_CLAIMED_예약을_취소가_먼저_잡고_있으면_복원은_한_번만_일어난다() {
        seed(PRODUCT_ID)
        val reservationId = claimed(PRODUCT_ID, 3)
        passClaimDeadline(reservationId)
        val job = job()

        val result = runWhileHolding(
            { connection ->
                connection.prepareStatement(
                    "UPDATE inventory_reservations SET status = 'CANCELLED' WHERE reservation_id = ? AND status = 'CLAIMED'"
                ).use { it.setString(1, reservationId); assertThat(it.executeUpdate()).isEqualTo(1) }
                connection.prepareStatement(
                    "UPDATE inventory_buckets SET stock_quantity = stock_quantity + 3 WHERE product_id = ? AND bucket_no = 0"
                ).use { it.setLong(1, PRODUCT_ID); assertThat(it.executeUpdate()).isEqualTo(1) }
            },
            "CLAIMED 바닥 만료",
            "inventory_reservations"
        ) {
            job.runCycle(LocalDateTime.now())
        }

        assertThat(result.isSuccess).isTrue()
        assertThat(status(reservationId)).isEqualTo("CANCELLED")
        assertThat(bucketSum(PRODUCT_ID)).isEqualTo(INITIAL)
        assertConserved(PRODUCT_ID)
    }

    @Test
    fun 바닥_만료가_잡으려는_CLAIMED_예약을_확정이_먼저_잡고_있으면_확정만_반영되고_복원하지_않는다() {
        seed(PRODUCT_ID)
        val reservationId = claimed(PRODUCT_ID, 3)
        passClaimDeadline(reservationId)
        val job = job()

        val result = runWhileHolding(
            { connection ->
                connection.prepareStatement(
                    "UPDATE inventory_reservations SET status = 'CONFIRMED' WHERE reservation_id = ? AND status = 'CLAIMED'"
                ).use { it.setString(1, reservationId); assertThat(it.executeUpdate()).isEqualTo(1) }
            },
            "CLAIMED 바닥 만료",
            "inventory_reservations"
        ) {
            job.runCycle(LocalDateTime.now())
        }

        assertThat(result.isSuccess).isTrue()
        assertThat(status(reservationId)).isEqualTo("CONFIRMED")
        assertThat(bucketSum(PRODUCT_ID)).isEqualTo(INITIAL - 3)
        assertConserved(PRODUCT_ID)
    }

    private fun job() = ReservationLinkCheckJob(
        reservationRepository, bucketStockService, port, transactionManager, SimpleMeterRegistry(),
        500, 2500, FLOOR_SECONDS, 24
    )

    private fun claimed(productId: Long, quantity: Int): String {
        val reservationId = commandService.reserveStock(productId, quantity)
        jdbcTemplate.update(
            "UPDATE inventory_reservations SET expires_at = ? WHERE reservation_id = ?",
            LocalDateTime.now().minusMinutes(1), reservationId
        )
        port.linked = port.linked + reservationId
        job().runCycle(LocalDateTime.now())
        assertThat(status(reservationId)).isEqualTo("CLAIMED")
        return reservationId
    }

    private fun passClaimDeadline(vararg reservationIds: String) {
        reservationIds.forEach {
            jdbcTemplate.update(
                "UPDATE inventory_reservations SET expires_at = ? WHERE reservation_id = ? AND status = 'CLAIMED'",
                LocalDateTime.now().minusMinutes(1), it
            )
        }
    }

    private fun seed(productId: Long) {
        (0 until 8).forEach { bucketNo ->
            jdbcTemplate.update(
                "INSERT INTO inventory_buckets (product_id, bucket_no, stock_quantity) VALUES (?, ?, ?)",
                productId, bucketNo, 10
            )
        }
    }

    private fun status(reservationId: String): String =
        jdbcTemplate.queryForObject("SELECT status FROM inventory_reservations WHERE reservation_id = ?", String::class.java, reservationId)!!

    private fun bucketSum(productId: Long): Int =
        jdbcTemplate.queryForObject("SELECT SUM(stock_quantity) FROM inventory_buckets WHERE product_id = ?", Int::class.java, productId)!!

    private fun heldQuantity(productId: Long): Int =
        jdbcTemplate.queryForObject(
            "SELECT COALESCE(SUM(quantity), 0) FROM inventory_reservations WHERE product_id = ? AND status IN ('RESERVED', 'CLAIMED', 'CONFIRMED')",
            Int::class.java, productId
        )!!

    private fun assertConserved(productId: Long) {
        val stocks = jdbcTemplate.queryForList(
            "SELECT stock_quantity FROM inventory_buckets WHERE product_id = ? ORDER BY bucket_no", Int::class.java, productId
        )
        assertThat(stocks).allMatch { it >= 0 }
        assertThat(stocks.sum() + heldQuantity(productId)).isEqualTo(INITIAL)
    }

    private class FakeLinkPort : ReservationLinkPort {
        @Volatile
        var linked: Set<String> = emptySet()

        @Volatile
        var failure: RuntimeException? = null

        override fun check(reservationIds: List<String>): ReservationLinkPort.LinkResult {
            failure?.let { throw it }
            return ReservationLinkPort.LinkResult(
                reservationIds.filter { it in linked },
                reservationIds.filterNot { it in linked }
            )
        }
    }

    companion object {
        private const val PRODUCT_ID = 15_001L
        private const val OTHER_PRODUCT_ID = 15_002L
        private const val INITIAL = 80
        private const val FLOOR_SECONDS = 600L
    }
}
