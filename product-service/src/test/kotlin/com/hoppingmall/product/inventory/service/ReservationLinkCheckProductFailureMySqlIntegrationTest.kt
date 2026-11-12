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

@DisplayName("예약 링크 확인 잡 상품별 실패 격리 MySQL 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@TestPropertySource(properties = ["inventory.mode=bucket", "inventory.bucket.count=8", "inventory.semaphore.permits=64"])
class ReservationLinkCheckProductFailureMySqlIntegrationTest : MySqlIntegrationTestSupport() {

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
    fun 미연결_만료_단계에서_한_상품이_실패해도_주기는_끝까지_돌고_다른_상품의_예약은_만료된다() {
        val broken = expiredReservationWithoutBuckets(BROKEN_PRODUCT_ID, 2)
        seed(NORMAL_PRODUCT_ID)
        val normal = expiredReservation(NORMAL_PRODUCT_ID, 3)

        val result = job().runCycle(LocalDateTime.now())

        assertThat(result.aborted).isEqualTo(1)
        assertThat(result.failed).isFalse()
        assertThat(asked.flatten()).containsExactlyInAnyOrder(broken, normal)
        assertThat(status(normal)).isEqualTo("EXPIRED")
        assertThat(bucketSum(NORMAL_PRODUCT_ID)).isEqualTo(80)
        assertThat(status(broken)).isEqualTo("RESERVED")
        assertThat(failedProducts()).isEqualTo(1.0)
    }

    @Test
    fun 바닥_만료_단계에서_한_상품이_실패해도_링크_확인이_실행되고_다른_상품의_예약이_처리된다() {
        val broken = expiredReservationWithoutBuckets(BROKEN_PRODUCT_ID, 2)
        makeCreatedBefore(broken, FLOOR_SECONDS + 60)
        seed(NORMAL_PRODUCT_ID)
        val normal = expiredReservation(NORMAL_PRODUCT_ID, 3)

        val result = job().runCycle(LocalDateTime.now())

        assertThat(result.floor).isZero()
        assertThat(result.batches).isEqualTo(1)
        assertThat(asked.flatten()).contains(normal)
        assertThat(status(normal)).isEqualTo("EXPIRED")
        assertThat(bucketSum(NORMAL_PRODUCT_ID)).isEqualTo(80)
        assertThat(status(broken)).isEqualTo("RESERVED")
        assertThat(failedProducts()).isGreaterThanOrEqualTo(1.0)
    }

    @Test
    fun 바닥_만료_단계에서_한_상품이_실패해도_바닥_시간이_지난_다른_상품의_예약은_만료된다() {
        val broken = expiredReservationWithoutBuckets(BROKEN_PRODUCT_ID, 2)
        makeCreatedBefore(broken, FLOOR_SECONDS + 60)
        seed(NORMAL_PRODUCT_ID)
        val old = expiredReservation(NORMAL_PRODUCT_ID, 3)
        makeCreatedBefore(old, FLOOR_SECONDS + 60)

        val result = job().runCycle(LocalDateTime.now())

        assertThat(result.floor).isEqualTo(1)
        assertThat(status(old)).isEqualTo("EXPIRED")
        assertThat(bucketSum(NORMAL_PRODUCT_ID)).isEqualTo(80)
        assertThat(status(broken)).isEqualTo("RESERVED")
    }

    @Test
    fun 실패한_상품의_버킷이_생기면_다음_주기에_그_예약이_만료되고_복원된다() {
        val broken = expiredReservationWithoutBuckets(BROKEN_PRODUCT_ID, 2)
        seed(NORMAL_PRODUCT_ID)
        val normal = expiredReservation(NORMAL_PRODUCT_ID, 3)
        job().runCycle(LocalDateTime.now())
        assertThat(status(broken)).isEqualTo("RESERVED")
        assertThat(status(normal)).isEqualTo("EXPIRED")

        seed(BROKEN_PRODUCT_ID, stock = 0)
        val result = job().runCycle(LocalDateTime.now())

        assertThat(result.aborted).isEqualTo(1)
        assertThat(status(broken)).isEqualTo("EXPIRED")
        assertThat(bucketSum(BROKEN_PRODUCT_ID)).isEqualTo(2)
        assertThat(bucketSum(NORMAL_PRODUCT_ID)).isEqualTo(80)
    }

    @Test
    fun 실패하는_상품이_상품_id_순서의_앞_가운데_뒤_어디에_있어도_나머지_상품은_처리된다() {
        listOf(0, 1, 2).forEach { brokenIndex ->
            val base = 16_100L + brokenIndex * 10
            val productIds = listOf(base, base + 1, base + 2)
            val reservations = productIds.mapIndexed { index, productId ->
                if (index == brokenIndex) {
                    expiredReservationWithoutBuckets(productId, 2)
                } else {
                    seed(productId)
                    expiredReservation(productId, 3)
                }
            }

            job().runCycle(LocalDateTime.now())

            productIds.forEachIndexed { index, productId ->
                if (index == brokenIndex) {
                    assertThat(status(reservations[index])).describedAs("실패 상품 위치=%s", brokenIndex).isEqualTo("RESERVED")
                } else {
                    assertThat(status(reservations[index])).describedAs("실패 상품 위치=%s, 상품=%s", brokenIndex, productId).isEqualTo("EXPIRED")
                    assertThat(bucketSum(productId)).describedAs("실패 상품 위치=%s, 상품=%s", brokenIndex, productId).isEqualTo(80)
                }
            }
            jdbcTemplate.update("DELETE FROM inventory_reservations")
        }
    }

    @Test
    fun 한_상품의_만료가_실패하면_그_상품에서_바꾼_예약_상태는_남지_않는다() {
        val first = expiredReservationWithoutBuckets(BROKEN_PRODUCT_ID, 2)
        val second = expiredReservationWithoutBuckets(BROKEN_PRODUCT_ID, 4)

        job().runCycle(LocalDateTime.now())

        assertThat(status(first)).isEqualTo("RESERVED")
        assertThat(status(second)).isEqualTo("RESERVED")
        assertThat(
            jdbcTemplate.queryForObject("SELECT COUNT(*) FROM inventory_buckets WHERE product_id = ?", Int::class.java, BROKEN_PRODUCT_ID)
        ).isZero()
        assertThat(failedProducts()).isEqualTo(1.0)
    }

    private fun job() = ReservationLinkCheckJob(
        reservationRepository, bucketStockService, port, transactionManager, meterRegistry, 500, 2500, FLOOR_SECONDS, 24
    )

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

    private fun makeCreatedBefore(reservationId: String, seconds: Long) {
        jdbcTemplate.update(
            "UPDATE inventory_reservations SET created_at = ? WHERE reservation_id = ?",
            LocalDateTime.now().minusSeconds(seconds), reservationId
        )
    }

    private fun status(reservationId: String): String =
        jdbcTemplate.queryForObject("SELECT status FROM inventory_reservations WHERE reservation_id = ?", String::class.java, reservationId)!!

    private fun bucketSum(productId: Long): Int =
        jdbcTemplate.queryForObject("SELECT SUM(stock_quantity) FROM inventory_buckets WHERE product_id = ?", Int::class.java, productId)!!

    companion object {
        private const val BROKEN_PRODUCT_ID = 16_001L
        private const val NORMAL_PRODUCT_ID = 16_002L
        private const val FLOOR_SECONDS = 600L
    }
}
