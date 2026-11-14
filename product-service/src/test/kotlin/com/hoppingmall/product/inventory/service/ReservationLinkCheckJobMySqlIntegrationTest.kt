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
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.transaction.PlatformTransactionManager
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@DisplayName("예약 링크 확인 잡 MySQL 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@TestPropertySource(properties = ["inventory.mode=bucket", "inventory.bucket.count=8", "inventory.semaphore.permits=64"])
class ReservationLinkCheckJobMySqlIntegrationTest : MySqlIntegrationTestSupport() {

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
    fun 만료_시각이_지난_예약_중_연결된_것은_CLAIMED로_24시간_미뤄지고_연결_안_된_것은_EXPIRED로_한_번만_복원된다() {
        seed(PRODUCT_ID)
        val linked = reserve(PRODUCT_ID, 3)
        val unlinked = reserve(PRODUCT_ID, 2)
        makeExpired(linked, unlinked)
        port.linked = setOf(linked)
        val now = LocalDateTime.now()

        val first = job().runCycle(now)
        val second = job().runCycle(LocalDateTime.now())

        assertThat(first.linked).isEqualTo(1)
        assertThat(first.aborted).isEqualTo(1)
        assertThat(first.failed).isFalse()
        assertThat(second.linked + second.aborted + second.floor).isZero()
        assertThat(status(linked)).isEqualTo("CLAIMED")
        assertThat(Duration.between(now, expiresAt(linked))).isBetween(Duration.ofHours(24).minusMinutes(1), Duration.ofHours(24).plusMinutes(1))
        assertThat(status(unlinked)).isEqualTo("EXPIRED")
        assertThat(bucketSum(PRODUCT_ID)).isEqualTo(77)
        assertConserved(PRODUCT_ID)
    }

    @Test
    fun 만료_시각이_지나지_않은_예약은_묻지_않는다() {
        seed(PRODUCT_ID)
        val alive = reserve(PRODUCT_ID, 3)
        val expired = reserve(PRODUCT_ID, 2)
        makeExpired(expired)

        job().runCycle(LocalDateTime.now())

        assertThat(port.askedIds()).containsExactly(expired)
        assertThat(status(alive)).isEqualTo("RESERVED")
        assertThat(status(expired)).isEqualTo("EXPIRED")
        assertThat(bucketSum(PRODUCT_ID)).isEqualTo(77)
    }

    @Test
    fun 만료_시각이_지난_예약이_없으면_포트를_부르지_않는다() {
        seed(PRODUCT_ID)
        val alive = reserve(PRODUCT_ID, 3)

        val result = job().runCycle(LocalDateTime.now())

        assertThat(port.asked).isEmpty()
        assertThat(result.batches).isZero()
        assertThat(status(alive)).isEqualTo("RESERVED")
        assertThat(bucketSum(PRODUCT_ID)).isEqualTo(77)
    }

    @Test
    fun 포트가_예외를_던지면_그_주기에는_아무것도_바뀌지_않는다() {
        seed(PRODUCT_ID)
        val expired = reserve(PRODUCT_ID, 2)
        makeExpired(expired)
        port.failure = IllegalStateException("연결 조회 실패")

        val result = job().runCycle(LocalDateTime.now())

        assertThat(result.failed).isTrue()
        assertThat(result.linked + result.aborted + result.floor).isZero()
        assertThat(port.askedIds()).containsExactly(expired)
        assertThat(status(expired)).isEqualTo("RESERVED")
        assertThat(bucketSum(PRODUCT_ID)).isEqualTo(78)
    }

    @Test
    fun 포트가_실패해도_생성된_지_바닥_시간이_지난_예약은_묻지_않고_만료된다() {
        seed(PRODUCT_ID)
        val old = reserve(PRODUCT_ID, 3)
        val recent = reserve(PRODUCT_ID, 2)
        makeExpired(old, recent)
        makeCreatedBefore(old, FLOOR_SECONDS + 1)
        port.failure = IllegalStateException("연결 조회 실패")

        val result = job().runCycle(LocalDateTime.now())

        assertThat(result.floor).isEqualTo(1)
        assertThat(result.failed).isTrue()
        assertThat(port.askedIds()).containsExactly(recent)
        assertThat(status(old)).isEqualTo("EXPIRED")
        assertThat(status(recent)).isEqualTo("RESERVED")
        assertThat(bucketSum(PRODUCT_ID)).isEqualTo(78)
        assertConserved(PRODUCT_ID)
    }

    @Test
    fun 잡이_만료시키려는_예약을_취소가_먼저_잡고_있으면_복원은_한_번만_일어난다() {
        seed(PRODUCT_ID)
        val reservationId = reserve(PRODUCT_ID, 3)
        makeExpired(reservationId)
        val job = job()

        val result = runWhileHolding(
            { connection ->
                connection.prepareStatement(
                    "UPDATE inventory_reservations SET status = 'CANCELLED' WHERE reservation_id = ? AND status = 'RESERVED'"
                ).use { it.setString(1, reservationId); assertThat(it.executeUpdate()).isEqualTo(1) }
                connection.prepareStatement(
                    "UPDATE inventory_buckets SET stock_quantity = stock_quantity + 3 WHERE product_id = ? AND bucket_no = 0"
                ).use { it.setLong(1, PRODUCT_ID); assertThat(it.executeUpdate()).isEqualTo(1) }
            },
            "링크 확인 잡",
            "inventory_reservations"
        ) {
            job.runCycle(LocalDateTime.now())
        }

        assertThat(result.isSuccess).isTrue()
        assertThat(port.askedIds()).containsExactly(reservationId)
        assertThat(status(reservationId)).isEqualTo("CANCELLED")
        assertThat(bucketSum(PRODUCT_ID)).isEqualTo(80)
        assertConserved(PRODUCT_ID)
    }

    @Test
    fun 잡이_만료시키려는_예약을_확정이_먼저_잡고_있으면_확정만_반영되고_복원하지_않는다() {
        seed(PRODUCT_ID)
        val reservationId = reserve(PRODUCT_ID, 3)
        makeExpired(reservationId)
        val job = job()

        val result = runWhileHolding(
            { connection ->
                connection.prepareStatement(
                    "UPDATE inventory_reservations SET status = 'CONFIRMED' WHERE reservation_id = ? AND status = 'RESERVED'"
                ).use { it.setString(1, reservationId); assertThat(it.executeUpdate()).isEqualTo(1) }
            },
            "링크 확인 잡",
            "inventory_reservations"
        ) {
            job.runCycle(LocalDateTime.now())
        }

        assertThat(result.isSuccess).isTrue()
        assertThat(status(reservationId)).isEqualTo("CONFIRMED")
        assertThat(bucketSum(PRODUCT_ID)).isEqualTo(77)
    }

    @Test
    fun 잡이_먼저_만료시킨_예약은_확정이_실패하고_재고는_복원된_채로_남는다() {
        seed(PRODUCT_ID)
        val reservationId = reserve(PRODUCT_ID, 3)
        makeExpired(reservationId)

        job().runCycle(LocalDateTime.now())

        assertThatThrownBy { commandService.confirmReservations(listOf(reservationId)) }
            .isInstanceOf(ReservationConfirmFailedException::class.java)
        assertThat(status(reservationId)).isEqualTo("EXPIRED")
        assertThat(bucketSum(PRODUCT_ID)).isEqualTo(80)
    }

    @Test
    fun 잡_두_개가_같은_대상을_동시에_돌아도_예약마다_한_번만_처리된다() {
        seed(PRODUCT_ID)
        seed(OTHER_PRODUCT_ID)
        val reservations = (1..10).flatMap { listOf(reserve(PRODUCT_ID, 1), reserve(OTHER_PRODUCT_ID, 2)) }
        makeExpired(*reservations.toTypedArray())
        val linked = reservations.filterIndexed { index, _ -> index % 4 < 2 }.toSet()
        port.linked = linked
        val deadlocksBefore = deadlocks()
        val start = CountDownLatch(1)
        val now = LocalDateTime.now()

        val futures = (1..2).map {
            val job = job()
            executor.submit<ReservationLinkCheckJob.CycleResult> { start.await(); job.runCycle(now) }
        }
        start.countDown()
        val results = futures.map { it.get(60, TimeUnit.SECONDS) }

        assertThat(results.sumOf { it.linked }).isEqualTo(10)
        assertThat(results.sumOf { it.aborted }).isEqualTo(10)
        assertThat(reservations.filter { it in linked }.map { status(it) }).containsOnly("CLAIMED")
        assertThat(reservations.filterNot { it in linked }.map { status(it) }).containsOnly("EXPIRED")
        assertThat(bucketSum(PRODUCT_ID)).isEqualTo(75)
        assertThat(bucketSum(OTHER_PRODUCT_ID)).isEqualTo(70)
        assertConserved(PRODUCT_ID)
        assertConserved(OTHER_PRODUCT_ID)
        assertThat(deadlocks() - deadlocksBefore).isZero()
    }

    @Test
    fun 남은_예약이_적어_상태_인덱스로_잠그는_계획에서도_잡_두_개가_동시에_돌면_교착하지_않는다() {
        seed(PRODUCT_ID)
        insertFinishedReservations(900)
        val reservations = (1..10).map { reserve(PRODUCT_ID, 1) }
        makeExpired(*reservations.toTypedArray())
        jdbcTemplate.queryForList("ANALYZE TABLE inventory_reservations")
        val linked = reservations.filterIndexed { index, _ -> index % 2 == 0 }.toSet()
        port.linked = linked
        val deadlocksBefore = deadlocks()
        val start = CountDownLatch(1)
        val now = LocalDateTime.now()

        val futures = (1..2).map {
            val job = job()
            executor.submit<ReservationLinkCheckJob.CycleResult> { start.await(); job.runCycle(now) }
        }
        start.countDown()
        val results = futures.map { it.get(60, TimeUnit.SECONDS) }

        assertThat(results.sumOf { it.linked }).isEqualTo(5)
        assertThat(results.sumOf { it.aborted }).isEqualTo(5)
        assertThat(reservations.filter { it in linked }.map { status(it) }).containsOnly("CLAIMED")
        assertThat(reservations.filterNot { it in linked }.map { status(it) }).containsOnly("EXPIRED")
        assertThat(bucketSum(PRODUCT_ID)).isEqualTo(75)
        assertConserved(PRODUCT_ID)
        assertThat(deadlocks() - deadlocksBefore).isZero()
    }

    @Test
    fun 대상이_묶음_크기보다_많으면_한_주기_안에서_묶음을_이어_모두_처리한다() {
        seed(PRODUCT_ID)
        val reservations = (1..8).map { reserve(PRODUCT_ID, 1) }
        makeExpired(*reservations.toTypedArray())

        val result = job(batchSize = 3).runCycle(LocalDateTime.now())

        assertThat(result.batches).isEqualTo(3)
        assertThat(result.aborted).isEqualTo(8)
        assertThat(port.asked.map { it.size }).containsExactly(3, 3, 2)
        assertThat(port.askedIds()).containsExactlyInAnyOrderElementsOf(reservations)
        assertThat(reservations.map { status(it) }).containsOnly("EXPIRED")
        assertThat(bucketSum(PRODUCT_ID)).isEqualTo(80)
    }

    @Test
    fun 링크_확인이_켜져_있으면_버킷_만료_잡은_아무것도_하지_않는다() {
        seed(PRODUCT_ID)
        val reservationId = reserve(PRODUCT_ID, 3)
        makeExpired(reservationId)
        val expiryJob = BucketReservationExpiryJob(
            reservationRepository, bucketStockService, transactionManager, SimpleMeterRegistry(), true, 1000
        )
        ReflectionTestUtils.setField(expiryJob, "linkCheckEnabled", true)

        expiryJob.expireReservations()

        assertThat(status(reservationId)).isEqualTo("RESERVED")
        assertThat(bucketSum(PRODUCT_ID)).isEqualTo(77)
    }

    private fun job(batchSize: Int = 500) = ReservationLinkCheckJob(
        reservationRepository, bucketStockService, port, transactionManager, SimpleMeterRegistry(),
        batchSize, 2500, FLOOR_SECONDS, 24
    )

    private fun seed(productId: Long) {
        (0 until 8).forEach { bucketNo ->
            jdbcTemplate.update(
                "INSERT INTO inventory_buckets (product_id, bucket_no, stock_quantity) VALUES (?, ?, ?)",
                productId, bucketNo, 10
            )
        }
    }

    private fun reserve(productId: Long, quantity: Int): String = commandService.reserveStock(productId, quantity)

    private fun insertFinishedReservations(count: Int) {
        jdbcTemplate.update(
            """
            INSERT INTO inventory_reservations (reservation_id, product_id, quantity, status, expires_at, created_at, updated_at)
            WITH RECURSIVE seq(n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < ?)
            SELECT UUID(), ? + (n % 50), 1, IF(n % 2 = 0, 'CONFIRMED', 'EXPIRED'),
                   NOW() - INTERVAL 1 DAY, NOW() - INTERVAL 1 DAY, NOW() - INTERVAL 1 DAY
            FROM seq
            """.trimIndent(),
            count, FINISHED_PRODUCT_ID_BASE
        )
    }

    private fun makeExpired(vararg reservationIds: String) {
        reservationIds.forEach {
            jdbcTemplate.update(
                "UPDATE inventory_reservations SET expires_at = ? WHERE reservation_id = ?",
                LocalDateTime.now().minusMinutes(1), it
            )
        }
    }

    private fun makeCreatedBefore(reservationId: String, seconds: Long) {
        jdbcTemplate.update(
            "UPDATE inventory_reservations SET created_at = ? WHERE reservation_id = ?",
            LocalDateTime.now().minusSeconds(seconds), reservationId
        )
    }

    private fun status(reservationId: String): String =
        jdbcTemplate.queryForObject("SELECT status FROM inventory_reservations WHERE reservation_id = ?", String::class.java, reservationId)!!

    private fun expiresAt(reservationId: String): LocalDateTime =
        jdbcTemplate.queryForObject("SELECT expires_at FROM inventory_reservations WHERE reservation_id = ?", LocalDateTime::class.java, reservationId)!!

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

    private fun deadlocks(): Long =
        jdbcTemplate.queryForObject(
            "SELECT `COUNT` FROM information_schema.INNODB_METRICS WHERE NAME = 'lock_deadlocks'",
            Long::class.java
        )!!

    private class FakeLinkPort : ReservationLinkPort {
        val asked = CopyOnWriteArrayList<List<String>>()

        @Volatile
        var linked: Set<String> = emptySet()

        @Volatile
        var failure: RuntimeException? = null

        fun askedIds(): List<String> = asked.flatten()

        override fun check(reservationIds: List<String>): ReservationLinkPort.LinkResult {
            asked.add(reservationIds.toList())
            failure?.let { throw it }
            return ReservationLinkPort.LinkResult(
                reservationIds.filter { it in linked },
                reservationIds.filterNot { it in linked }
            )
        }
    }

    companion object {
        private const val PRODUCT_ID = 14_001L
        private const val OTHER_PRODUCT_ID = 14_002L
        private const val FINISHED_PRODUCT_ID_BASE = 14_100L
        private const val INITIAL = 80
        private const val FLOOR_SECONDS = 600L
    }
}
