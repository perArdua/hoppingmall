package com.hoppingmall.product.inventory.service

import com.hoppingmall.product.inventory.domain.Inventory
import com.hoppingmall.product.inventory.domain.InventoryReservation
import com.hoppingmall.product.inventory.domain.repository.InventoryRepository
import com.hoppingmall.product.inventory.domain.repository.InventoryReservationRepository
import com.hoppingmall.product.inventory.enums.ReservationStatus
import com.hoppingmall.product.support.MySqlIntegrationTestSupport
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDateTime
import java.util.UUID

@DisplayName("버킷 예약 만료 잡 MySQL 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class BucketReservationExpiryJobMySqlIntegrationTest : MySqlIntegrationTestSupport() {

    @Autowired
    private lateinit var inventoryReservationRepository: InventoryReservationRepository

    @Autowired
    private lateinit var inventoryRepository: InventoryRepository

    @Autowired
    private lateinit var bucketStockService: BucketStockService

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    @Autowired
    private lateinit var transactionTemplate: TransactionTemplate

    private val meterRegistry = SimpleMeterRegistry()

    @Test
    fun 만료된_예약만_EXPIRED로_바꾸고_수량을_버킷에_돌려준다() {
        initBuckets(PRODUCT_ID, 80)
        val expiredA = reservation(PRODUCT_ID, 3, ReservationStatus.RESERVED, past())
        val expiredB = reservation(PRODUCT_ID, 4, ReservationStatus.RESERVED, past())
        val alive = reservation(PRODUCT_ID, 5, ReservationStatus.RESERVED, future())
        val confirmed = reservation(PRODUCT_ID, 6, ReservationStatus.CONFIRMED, past())
        val cancelled = reservation(PRODUCT_ID, 7, ReservationStatus.CANCELLED, past())
        val heldBefore = bucketTotal(PRODUCT_ID) + reservedQuantity(PRODUCT_ID)

        job().expireReservations()

        assertThat(status(expiredA)).isEqualTo("EXPIRED")
        assertThat(status(expiredB)).isEqualTo("EXPIRED")
        assertThat(status(alive)).isEqualTo("RESERVED")
        assertThat(status(confirmed)).isEqualTo("CONFIRMED")
        assertThat(status(cancelled)).isEqualTo("CANCELLED")
        assertThat(bucketTotal(PRODUCT_ID)).isEqualTo(87)
        assertThat(bucketTotal(PRODUCT_ID) + reservedQuantity(PRODUCT_ID)).isEqualTo(heldBefore)
    }

    @Test
    fun 청크_크기보다_만료_대상이_많으면_한_주기_안에서_청크를_이어_모두_처리한다() {
        initBuckets(PRODUCT_ID, 80)
        val ids = (1..8).map { reservation(PRODUCT_ID, 1, ReservationStatus.RESERVED, past()) }

        job(chunkSize = 3).expireReservations()

        assertThat(ids.map { status(it) }).containsOnly("EXPIRED")
        assertThat(bucketTotal(PRODUCT_ID)).isEqualTo(88)
        val batchSize = meterRegistry.summary("inventory.expiry.batch.size")
        assertThat(batchSize.count()).isEqualTo(3)
        assertThat(batchSize.totalAmount()).isEqualTo(8.0)
        assertThat(batchSize.max()).isEqualTo(3.0)
        assertThat(meterRegistry.timer("inventory.expiry.tx").count()).isEqualTo(3)
    }

    @Test
    fun 만료_청크가_취소와_겹치면_취소된_예약의_수량을_다시_복원하지_않는다() {
        initBuckets(PRODUCT_ID, 80)
        val first = reservation(PRODUCT_ID, 2, ReservationStatus.RESERVED, past())
        val cancelling = reservation(PRODUCT_ID, 3, ReservationStatus.RESERVED, past())
        val last = reservation(PRODUCT_ID, 4, ReservationStatus.RESERVED, past())
        val job = job()

        val result = runWhileHolding(
            { connection ->
                connection.prepareStatement(
                    "UPDATE inventory_reservations SET status = 'CANCELLED' WHERE reservation_id = ? AND status = 'RESERVED'"
                ).use { it.setString(1, cancelling); assertThat(it.executeUpdate()).isEqualTo(1) }
                connection.prepareStatement(
                    "UPDATE inventory_buckets SET stock_quantity = stock_quantity + 3 WHERE product_id = ? AND bucket_no = 0"
                ).use { it.setLong(1, PRODUCT_ID); assertThat(it.executeUpdate()).isEqualTo(1) }
            },
            "버킷 만료 잡",
            "inventory_reservations"
        ) {
            job.expireReservations()
        }

        assertThat(result.isSuccess).isTrue()
        assertThat(status(first)).isEqualTo("EXPIRED")
        assertThat(status(cancelling)).isEqualTo("CANCELLED")
        assertThat(status(last)).isEqualTo("EXPIRED")
        assertThat(bucketTotal(PRODUCT_ID)).isEqualTo(80 + 3 + 2 + 4)
        assertThat(meterRegistry.counter("inventory.expiry.cas_mismatch").count()).isEqualTo(1.0)
        assertThat(meterRegistry.timer("inventory.expiry.tx").count()).isEqualTo(2)
        val batchSize = meterRegistry.summary("inventory.expiry.batch.size")
        assertThat(batchSize.count()).isEqualTo(1)
        assertThat(batchSize.totalAmount()).isEqualTo(2.0)
    }

    @Test
    fun 버킷_행이_없는_상품의_복원이_실패해도_다른_상품의_만료는_처리된다() {
        val orphan = reservation(PRODUCT_ID, 2, ReservationStatus.RESERVED, past())
        initBuckets(OTHER_PRODUCT_ID, 80)
        val other = reservation(OTHER_PRODUCT_ID, 5, ReservationStatus.RESERVED, past())

        job().expireReservations()

        assertThat(status(orphan)).isEqualTo("RESERVED")
        assertThat(bucketStockService.hasBuckets(PRODUCT_ID)).isFalse()
        assertThat(status(other)).isEqualTo("EXPIRED")
        assertThat(bucketTotal(OTHER_PRODUCT_ID)).isEqualTo(85)
    }

    @Test
    fun 기존_만료_스케줄러는_한_주기에_상한만큼만_처리하고_나머지는_다음_주기에_처리한다() {
        inventoryRepository.saveAndFlush(Inventory.create(productId = PRODUCT_ID, stockQuantity = 10))
        val ids = (1..5).map { reservation(PRODUCT_ID, 1, ReservationStatus.RESERVED, past()) }
        val scheduler = ReservationExpiryScheduler(inventoryReservationRepository, inventoryRepository)
        ReflectionTestUtils.setField(scheduler, "expiryLimit", 3)

        transactionTemplate.execute { scheduler.expireReservations() }

        assertThat(ids.count { status(it) == "EXPIRED" }).isEqualTo(3)
        assertThat(singleStock(PRODUCT_ID)).isEqualTo(13)

        transactionTemplate.execute { scheduler.expireReservations() }

        assertThat(ids.map { status(it) }).containsOnly("EXPIRED")
        assertThat(singleStock(PRODUCT_ID)).isEqualTo(15)
    }

    private fun job(chunkSize: Int = 1000) = BucketReservationExpiryJob(
        inventoryReservationRepository, bucketStockService, transactionManager, meterRegistry, true, chunkSize
    )

    private fun initBuckets(productId: Long, total: Int) {
        transactionTemplate.execute { bucketStockService.initBuckets(productId, total) }
    }

    private fun reservation(productId: Long, quantity: Int, status: ReservationStatus, expiresAt: LocalDateTime): String =
        inventoryReservationRepository.saveAndFlush(
            InventoryReservation(
                reservationId = UUID.randomUUID().toString(),
                productId = productId,
                quantity = quantity,
                status = status,
                expiresAt = expiresAt
            )
        ).reservationId

    private fun past(): LocalDateTime = LocalDateTime.now().minusMinutes(5)

    private fun future(): LocalDateTime = LocalDateTime.now().plusMinutes(5)

    private fun status(reservationId: String): String =
        jdbcTemplate.queryForObject("SELECT status FROM inventory_reservations WHERE reservation_id = ?", String::class.java, reservationId)!!

    private fun bucketTotal(productId: Long): Int =
        jdbcTemplate.queryForObject("SELECT SUM(stock_quantity) FROM inventory_buckets WHERE product_id = ?", Int::class.java, productId)!!

    private fun reservedQuantity(productId: Long): Int =
        jdbcTemplate.queryForObject(
            "SELECT COALESCE(SUM(quantity), 0) FROM inventory_reservations WHERE product_id = ? AND status = 'RESERVED'",
            Int::class.java,
            productId
        )!!

    private fun singleStock(productId: Long): Int =
        jdbcTemplate.queryForObject("SELECT stock_quantity FROM inventories WHERE product_id = ?", Int::class.java, productId)!!

    companion object {
        private const val PRODUCT_ID = 4001L
        private const val OTHER_PRODUCT_ID = 4002L
    }
}
