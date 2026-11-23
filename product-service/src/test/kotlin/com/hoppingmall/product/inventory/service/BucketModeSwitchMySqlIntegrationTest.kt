package com.hoppingmall.product.inventory.service

import com.hoppingmall.product.inventory.domain.Inventory
import com.hoppingmall.product.inventory.domain.InventoryReservation
import com.hoppingmall.product.inventory.domain.repository.InventoryRepository
import com.hoppingmall.product.inventory.domain.repository.InventoryReservationRepository
import com.hoppingmall.product.inventory.dto.request.InventoryUpdateRequest
import com.hoppingmall.product.inventory.enums.ReservationStatus
import com.hoppingmall.product.inventory.exception.InventoryNotFoundException
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
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.TimeUnit

@DisplayName("데이터가 있는 DB를 bucket 모드로 바꾼 뒤 버킷이 없는 상품 MySQL 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@TestPropertySource(properties = ["inventory.mode=bucket", "inventory.bucket.count=8", "inventory.semaphore.permits=64"])
class BucketModeSwitchMySqlIntegrationTest : MySqlIntegrationTestSupport() {

    @Autowired
    private lateinit var commandService: InventoryCommandService

    @Autowired
    private lateinit var bucketStockService: BucketStockService

    @Autowired
    private lateinit var inventoryRepository: InventoryRepository

    @Autowired
    private lateinit var reservationRepository: InventoryReservationRepository

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    @Autowired
    private lateinit var transactionTemplate: TransactionTemplate

    @Test
    fun 버킷이_없는_상품의_만료_예약은_지금_재고로_버킷을_만든_뒤_수량을_돌려준다() {
        singleModeInventory(PRODUCT_ID, 10)
        val expired = reservation(PRODUCT_ID, 3, LocalDateTime.now().minusMinutes(5))

        expiryJob().expireReservations()

        assertThat(status(expired)).isEqualTo("EXPIRED")
        assertThat(bucketRows(PRODUCT_ID)).isEqualTo(8)
        assertThat(bucketTotal(PRODUCT_ID)).isEqualTo(13)
    }

    @Test
    fun 버킷이_없는_상품의_예약_취소는_버킷을_만들고_재고를_돌려준다() {
        singleModeInventory(PRODUCT_ID, 10)
        val reserved = reservation(PRODUCT_ID, 2, LocalDateTime.now().plusMinutes(5))

        commandService.cancelReservation(reserved)

        assertThat(status(reserved)).isEqualTo("CANCELLED")
        assertThat(bucketRows(PRODUCT_ID)).isEqualTo(8)
        assertThat(bucketTotal(PRODUCT_ID)).isEqualTo(12)
    }

    @Test
    fun 버킷이_없는_상품의_재고_수정은_버킷을_만들고_새_재고를_나눈다() {
        singleModeInventory(PRODUCT_ID, 10)

        commandService.updateStock(PRODUCT_ID, InventoryUpdateRequest(20, expectedStockQuantity = 10))

        assertThat(bucketRows(PRODUCT_ID)).isEqualTo(8)
        assertThat(bucketTotal(PRODUCT_ID)).isEqualTo(20)
        assertThat(inventoryRowStock(PRODUCT_ID)).isEqualTo(20)
    }

    @Test
    fun 버킷이_없는_상품도_예약하면_지금_재고에서_뺀다() {
        singleModeInventory(PRODUCT_ID, 10)

        val reservationIds = commandService.batchReserveStock(listOf(PRODUCT_ID to 3))

        assertThat(status(reservationIds.getValue(PRODUCT_ID))).isEqualTo("RESERVED")
        assertThat(bucketRows(PRODUCT_ID)).isEqualTo(8)
        assertThat(bucketTotal(PRODUCT_ID)).isEqualTo(7)
    }

    @Test
    fun 재고_행이_없는_상품은_버킷을_만들지_않고_재고_없음_예외를_던진다() {
        assertThatThrownBy { transactionTemplate.execute { bucketStockService.restore(PRODUCT_ID, 1) } }
            .isInstanceOf(InventoryNotFoundException::class.java)

        assertThat(bucketRows(PRODUCT_ID)).isZero()
    }

    @Test
    fun 같은_상품의_버킷을_다른_트랜잭션이_만드는_중이면_기다렸다가_그_버킷에_복원한다() {
        singleModeInventory(PRODUCT_ID, 10)

        val result = runWhileHolding(
            { connection -> lockInventoryAndInsertBuckets(connection, PRODUCT_ID, 10) },
            "버킷 없는 상품 복원",
            "inventories"
        ) {
            transactionTemplate.execute { bucketStockService.restore(PRODUCT_ID, 2) }
        }

        assertThat(result.exceptionOrNull()).isNull()
        assertThat(bucketRows(PRODUCT_ID)).isEqualTo(8)
        assertThat(bucketTotal(PRODUCT_ID)).isEqualTo(12)
    }

    @Test
    fun 이웃한_다른_상품의_버킷을_만드는_트랜잭션이_있어도_기다리지_않는다() {
        singleModeInventory(PRODUCT_ID, 10)
        singleModeInventory(NEXT_PRODUCT_ID, 30)

        dataSource.connection.use { holder ->
            holder.autoCommit = false
            try {
                lockInventoryAndInsertBuckets(holder, NEXT_PRODUCT_ID, 30)

                val pending = executor.submit<Result<Unit>> {
                    runCatching { transactionTemplate.executeWithoutResult { bucketStockService.restore(PRODUCT_ID, 2) } }
                }

                assertThat(pending.get(10, TimeUnit.SECONDS).exceptionOrNull()).isNull()
                holder.commit()
            } finally {
                holder.rollback()
                holder.autoCommit = true
            }
        }

        assertThat(bucketTotal(PRODUCT_ID)).isEqualTo(12)
        assertThat(bucketTotal(NEXT_PRODUCT_ID)).isEqualTo(30)
    }

    private fun expiryJob() = BucketReservationExpiryJob(
        reservationRepository, bucketStockService, transactionManager, SimpleMeterRegistry(), true, 1000
    )

    private fun singleModeInventory(productId: Long, stock: Int) {
        inventoryRepository.saveAndFlush(Inventory.create(productId = productId, stockQuantity = stock))
    }

    private fun lockInventoryAndInsertBuckets(connection: java.sql.Connection, productId: Long, stock: Int) {
        connection.prepareStatement("SELECT stock_quantity FROM inventories WHERE product_id = ? FOR UPDATE").use {
            it.setLong(1, productId)
            it.executeQuery().use { rs -> assertThat(rs.next()).isTrue() }
        }
        (0 until 8).forEach { bucketNo ->
            connection.prepareStatement("INSERT INTO inventory_buckets (product_id, bucket_no, stock_quantity) VALUES (?, ?, ?)").use {
                it.setLong(1, productId)
                it.setInt(2, bucketNo)
                it.setInt(3, stock / 8 + if (bucketNo == 0) stock % 8 else 0)
                it.executeUpdate()
            }
        }
    }

    private fun reservation(productId: Long, quantity: Int, expiresAt: LocalDateTime): String =
        reservationRepository.saveAndFlush(
            InventoryReservation(
                reservationId = UUID.randomUUID().toString(),
                productId = productId,
                quantity = quantity,
                status = ReservationStatus.RESERVED,
                expiresAt = expiresAt
            )
        ).reservationId

    private fun status(reservationId: String): String =
        jdbcTemplate.queryForObject("SELECT status FROM inventory_reservations WHERE reservation_id = ?", String::class.java, reservationId)!!

    private fun bucketRows(productId: Long): Int =
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM inventory_buckets WHERE product_id = ?", Int::class.java, productId)!!

    private fun bucketTotal(productId: Long): Int =
        jdbcTemplate.queryForObject("SELECT COALESCE(SUM(stock_quantity), 0) FROM inventory_buckets WHERE product_id = ?", Int::class.java, productId)!!

    private fun inventoryRowStock(productId: Long): Int =
        jdbcTemplate.queryForObject("SELECT stock_quantity FROM inventories WHERE product_id = ?", Int::class.java, productId)!!

    companion object {
        private const val PRODUCT_ID = 9431L
        private const val NEXT_PRODUCT_ID = 9432L
    }
}
