package com.hoppingmall.product.inventory.service

import com.hoppingmall.product.inventory.domain.Inventory
import com.hoppingmall.product.inventory.domain.repository.InventoryRepository
import com.hoppingmall.product.inventory.domain.repository.InventoryReservationRepository
import com.hoppingmall.product.support.MySqlIntegrationTestSupport
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.PlatformTransactionManager
import java.time.LocalDateTime

@DisplayName("링크 확인이 꺼진 기본 설정의 CLAIMED 예약")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@TestPropertySource(properties = ["inventory.mode=bucket", "inventory.bucket.count=8", "inventory.semaphore.permits=64"])
class ClaimedReservationFloorDefaultOffMySqlIntegrationTest : MySqlIntegrationTestSupport() {

    @Autowired
    private lateinit var commandService: InventoryCommandService

    @Autowired
    private lateinit var reservationRepository: InventoryReservationRepository

    @Autowired
    private lateinit var inventoryRepository: InventoryRepository

    @Autowired
    private lateinit var bucketStockService: BucketStockService

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    private fun contextRunner() = ApplicationContextRunner()
        .withBean(InventoryReservationRepository::class.java, { mock<InventoryReservationRepository>() })
        .withBean(BucketStockService::class.java, {
            mock<BucketStockService>().also { whenever(it.enabled).thenReturn(true) }
        })
        .withBean(ReservationLinkPort::class.java, { mock<ReservationLinkPort>() })
        .withBean(PlatformTransactionManager::class.java, { mock<PlatformTransactionManager>() })
        .withBean(MeterRegistry::class.java, { SimpleMeterRegistry() })
        .withUserConfiguration(ReservationLinkCheckJob::class.java)

    @Test
    fun 기본_설정에서는_CLAIMED_바닥_만료를_도는_링크_확인_잡이_등록되지_않는다() {
        contextRunner().run { context ->
            assertThat(context).hasNotFailed()
            assertThat(context).doesNotHaveBean(ReservationLinkCheckJob::class.java)
        }
        contextRunner().withPropertyValues("inventory.mode=bucket").run { context ->
            assertThat(context).hasNotFailed()
            assertThat(context).doesNotHaveBean(ReservationLinkCheckJob::class.java)
        }
    }

    @Test
    fun 링크_확인이_꺼진_버킷_만료_잡은_선점_기한이_지난_CLAIMED_예약을_건드리지_않는다() {
        seedBuckets(PRODUCT_ID)
        val reservationId = claimedPastDeadline(PRODUCT_ID, 3)
        val expiryJob = BucketReservationExpiryJob(
            reservationRepository, bucketStockService, transactionManager, SimpleMeterRegistry(), true, 1000
        )

        expiryJob.expireReservations()

        assertThat(status(reservationId)).isEqualTo("CLAIMED")
        assertThat(bucketSum(PRODUCT_ID)).isEqualTo(77)
    }

    @Test
    fun single_모드_만료_스케줄러는_선점_기한이_지난_CLAIMED_예약을_건드리지_않는다() {
        inventoryRepository.save(Inventory.create(productId = SINGLE_PRODUCT_ID, stockQuantity = 7))
        val reservationId = insertClaimedPastDeadline(SINGLE_PRODUCT_ID, 3)

        ReservationExpiryScheduler(reservationRepository, inventoryRepository).expireReservations()

        assertThat(status(reservationId)).isEqualTo("CLAIMED")
        assertThat(
            jdbcTemplate.queryForObject("SELECT stock_quantity FROM inventories WHERE product_id = ?", Int::class.java, SINGLE_PRODUCT_ID)
        ).isEqualTo(7)
    }

    private fun claimedPastDeadline(productId: Long, quantity: Int): String {
        val reservationId = commandService.reserveStock(productId, quantity)
        jdbcTemplate.update(
            "UPDATE inventory_reservations SET status = 'CLAIMED', expires_at = ? WHERE reservation_id = ?",
            LocalDateTime.now().minusHours(1), reservationId
        )
        return reservationId
    }

    private fun insertClaimedPastDeadline(productId: Long, quantity: Int): String {
        val reservationId = "claimed-$productId"
        val now = LocalDateTime.now()
        jdbcTemplate.update(
            """
            INSERT INTO inventory_reservations (reservation_id, product_id, quantity, status, expires_at, created_at, updated_at)
            VALUES (?, ?, ?, 'CLAIMED', ?, ?, ?)
            """.trimIndent(),
            reservationId, productId, quantity, now.minusHours(1), now.minusHours(26), now.minusHours(25)
        )
        return reservationId
    }

    private fun seedBuckets(productId: Long) {
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

    companion object {
        private const val PRODUCT_ID = 15_101L
        private const val SINGLE_PRODUCT_ID = 15_102L
    }
}
