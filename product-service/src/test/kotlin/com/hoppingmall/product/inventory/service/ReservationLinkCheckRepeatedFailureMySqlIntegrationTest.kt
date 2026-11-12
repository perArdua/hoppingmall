package com.hoppingmall.product.inventory.service

import com.hoppingmall.product.inventory.domain.repository.InventoryReservationRepository
import com.hoppingmall.product.support.MySqlIntegrationTestSupport
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.PlatformTransactionManager
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

@DisplayName("예약 링크 확인 잡 실패 상품 반복 질의 MySQL 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@TestPropertySource(properties = ["inventory.mode=bucket", "inventory.bucket.count=8", "inventory.semaphore.permits=64"])
class ReservationLinkCheckRepeatedFailureMySqlIntegrationTest : MySqlIntegrationTestSupport() {

    @Autowired
    private lateinit var commandService: InventoryCommandService

    @Autowired
    private lateinit var reservationRepository: InventoryReservationRepository

    @Autowired
    private lateinit var bucketStockService: BucketStockService

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    private val asked = CopyOnWriteArrayList<List<String>>()

    private val meterRegistry = SimpleMeterRegistry()

    private val port = object : ReservationLinkPort {
        override fun check(reservationIds: List<String>): ReservationLinkPort.LinkResult {
            asked.add(reservationIds.toList())
            return ReservationLinkPort.LinkResult(emptyList(), reservationIds)
        }
    }

    @Test
    fun 실패하는_상품의_만료_예약이_묶음을_채워도_한_주기에_한_번만_묻고_id가_큰_상품을_처리한다() {
        val broken = (1..BATCH_SIZE).map { expiredReservationWithoutBuckets(BROKEN_PRODUCT_ID, 2) }
        seed(NORMAL_PRODUCT_ID)
        val normal = expiredReservation(NORMAL_PRODUCT_ID, 3)

        val result = job().runCycle(LocalDateTime.now())

        broken.forEach { assertThat(askedCount(it)).describedAs("예약=%s", it).isEqualTo(1) }
        assertThat(askedCount(normal)).isEqualTo(1)
        assertThat(status(normal)).isEqualTo("EXPIRED")
        assertThat(bucketSum(NORMAL_PRODUCT_ID)).isEqualTo(80)
        broken.forEach { assertThat(status(it)).isEqualTo("RESERVED") }
        assertThat(result.aborted).isEqualTo(1)
        assertThat(result.failed).isFalse()
        assertThat(failedProducts()).isEqualTo(1.0)
    }

    @Test
    fun 실패_상품과_정상_상품이_한_묶음에_섞여도_실패_상품은_한_번만_묻고_정상_상품의_예약은_모두_처리된다() {
        val broken = (1..2).map { expiredReservationWithoutBuckets(BROKEN_PRODUCT_ID, 2) }
        seed(NORMAL_PRODUCT_ID)
        val normal = (1..3).map { expiredReservation(NORMAL_PRODUCT_ID, 1) }

        val result = job().runCycle(LocalDateTime.now())

        broken.forEach { assertThat(askedCount(it)).describedAs("예약=%s", it).isEqualTo(1) }
        normal.forEach { assertThat(status(it)).describedAs("예약=%s", it).isEqualTo("EXPIRED") }
        assertThat(bucketSum(NORMAL_PRODUCT_ID)).isEqualTo(80)
        broken.forEach { assertThat(status(it)).isEqualTo("RESERVED") }
        assertThat(result.aborted).isEqualTo(3)
        assertThat(failedProducts()).isEqualTo(1.0)
    }

    @Test
    fun 실패한_상품은_다음_주기에_다시_묻고_원인이_풀리면_만료되어_복원된다() {
        val broken = (1..BATCH_SIZE).map { expiredReservationWithoutBuckets(BROKEN_PRODUCT_ID, 2) }
        seed(NORMAL_PRODUCT_ID)
        val normal = expiredReservation(NORMAL_PRODUCT_ID, 3)

        job().runCycle(LocalDateTime.now())
        job().runCycle(LocalDateTime.now())

        broken.forEach { assertThat(askedCount(it)).describedAs("예약=%s", it).isEqualTo(2) }
        broken.forEach { assertThat(status(it)).isEqualTo("RESERVED") }
        assertThat(status(normal)).isEqualTo("EXPIRED")

        seed(BROKEN_PRODUCT_ID, stock = 0)
        val result = job().runCycle(LocalDateTime.now())

        assertThat(result.aborted).isEqualTo(BATCH_SIZE)
        broken.forEach { assertThat(status(it)).isEqualTo("EXPIRED") }
        assertThat(bucketSum(BROKEN_PRODUCT_ID)).isEqualTo(BATCH_SIZE * 2)
    }

    private fun job() = ReservationLinkCheckJob(
        reservationRepository, bucketStockService, port, transactionManager, meterRegistry, BATCH_SIZE, 2500, FLOOR_SECONDS, 24
    )

    private fun askedCount(reservationId: String): Int = asked.flatten().count { it == reservationId }

    private fun failedProducts(): Double = meterRegistry.counter("inventory.linkcheck.failed_products").count()

    private fun seed(productId: Long, stock: Int = 10) {
        (0 until 8).forEach { bucketNo ->
            jdbcTemplate.update(
                "INSERT INTO inventory_buckets (product_id, bucket_no, stock_quantity) VALUES (?, ?, ?)",
                productId, bucketNo, stock
            )
        }
    }

    private fun expiredReservation(productId: Long, quantity: Int): String {
        val reservationId = commandService.reserveStock(productId, quantity)
        jdbcTemplate.update(
            "UPDATE inventory_reservations SET expires_at = ? WHERE reservation_id = ?",
            LocalDateTime.now().minusMinutes(1), reservationId
        )
        return reservationId
    }

    private fun expiredReservationWithoutBuckets(productId: Long, quantity: Int): String {
        val reservationId = UUID.randomUUID().toString()
        val now = LocalDateTime.now()
        jdbcTemplate.update(
            "INSERT INTO inventory_reservations (created_at, expires_at, product_id, quantity, reservation_id, status, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, 'RESERVED', ?)",
            now.minusMinutes(2), now.minusMinutes(1), productId, quantity, reservationId, now
        )
        return reservationId
    }

    private fun status(reservationId: String): String =
        jdbcTemplate.queryForObject("SELECT status FROM inventory_reservations WHERE reservation_id = ?", String::class.java, reservationId)!!

    private fun bucketSum(productId: Long): Int =
        jdbcTemplate.queryForObject("SELECT SUM(stock_quantity) FROM inventory_buckets WHERE product_id = ?", Int::class.java, productId)!!

    companion object {
        private const val BROKEN_PRODUCT_ID = 16_201L
        private const val NORMAL_PRODUCT_ID = 16_202L
        private const val BATCH_SIZE = 3
        private const val FLOOR_SECONDS = 600L
    }
}
