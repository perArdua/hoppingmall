package com.hoppingmall.product.inventory.service

import com.hoppingmall.product.common.enums.ProductStatus
import com.hoppingmall.product.inventory.domain.Inventory
import com.hoppingmall.product.inventory.domain.repository.InventoryRepository
import com.hoppingmall.product.inventory.domain.repository.InventoryReservationRepository
import com.hoppingmall.product.inventory.dto.request.InventoryInitRequest
import com.hoppingmall.product.inventory.dto.request.InventoryUpdateRequest
import com.hoppingmall.product.inventory.exception.InventoryInsufficientStockException
import com.hoppingmall.product.product.domain.Product
import com.hoppingmall.product.product.domain.repository.ProductRepository
import com.hoppingmall.product.support.MySqlIntegrationTestSupport
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.PlatformTransactionManager
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

@DisplayName("bucket 모드 재고 MySQL 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@TestPropertySource(properties = ["inventory.mode=bucket", "inventory.bucket.count=8"])
class BucketInventoryMySqlIntegrationTest : MySqlIntegrationTestSupport() {

    @Autowired
    private lateinit var commandService: InventoryCommandService

    @Autowired
    private lateinit var queryService: InventoryQueryService

    @Autowired
    private lateinit var bucketStockService: BucketStockService

    @Autowired
    private lateinit var reservationRepository: InventoryReservationRepository

    @Autowired
    private lateinit var inventoryRepository: InventoryRepository

    @Autowired
    private lateinit var productRepository: ProductRepository

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    @Autowired
    private lateinit var meterRegistry: MeterRegistry

    private val createdProductIds = mutableListOf<Long>()

    @AfterEach
    fun deleteCreatedProducts() {
        createdProductIds.forEach { jdbcTemplate.update("DELETE FROM products WHERE id = ?", it) }
    }

    @Test
    fun 스레드_32개가_단건_예약_확정_취소와_배치_예약을_섞어도_보존식이_성립하고_음수_버킷과_데드락이_없다() {
        val productId = 10_001L
        val other = 10_002L
        val initial = 400
        seedWithInventory(productId, List(8) { initial / 8 })
        seedWithInventory(other, List(8) { initial / 8 })
        val deadlocksBefore = deadlocks()
        val successes = AtomicInteger()
        val batchSuccesses = AtomicInteger()
        val insufficient = AtomicInteger()

        val errors = runConcurrently(32) { thread ->
            val random = Random(thread)
            repeat(15) {
                try {
                    if (random.nextInt(4) == 0) {
                        val items = if (thread % 2 == 0) listOf(productId to 1, other to 2) else listOf(other to 2, productId to 1)
                        commandService.batchReserveStock(items)
                        batchSuccesses.incrementAndGet()
                    } else {
                        val reservationId = commandService.reserveStock(productId, random.nextInt(1, 4))
                        successes.incrementAndGet()
                        when (random.nextInt(3)) {
                            0 -> commandService.confirmReservations(listOf(reservationId))
                            1 -> commandService.cancelReservation(reservationId)
                        }
                    }
                } catch (e: InventoryInsufficientStockException) {
                    insufficient.incrementAndGet()
                }
            }
        }

        assertThat(errors).isEmpty()
        assertThat(successes.get()).isPositive()
        assertThat(batchSuccesses.get()).isPositive()
        assertThat(insufficient.get()).isPositive()
        assertConserved(productId, initial)
        assertConserved(other, initial)
        assertThat(inventoryRowStock(productId)).isEqualTo(initial)
        assertThat(inventoryRowStock(other)).isEqualTo(initial)
        assertThat(deadlocks() - deadlocksBefore).isZero()
    }

    @Test
    fun 다품목_배치를_역순으로_섞어_동시에_예약해도_두_상품_모두_보존식이_성립하고_데드락이_없다() {
        val first = 10_101L
        val second = 10_102L
        seed(first, List(8) { 10 })
        seed(second, List(8) { 10 })
        val deadlocksBefore = deadlocks()

        val errors = runConcurrently(32) { thread ->
            val items = if (thread % 2 == 0) listOf(first to 1, second to 2) else listOf(second to 2, first to 1)
            repeat(10) {
                try {
                    commandService.batchReserveStock(items)
                } catch (e: InventoryInsufficientStockException) {
                    Unit
                }
            }
        }

        assertThat(errors).isEmpty()
        assertConserved(first, 80)
        assertConserved(second, 80)
        assertThat(bucketStocks(second).sum()).isZero()
        assertThat(deadlocks() - deadlocksBefore).isZero()
    }

    @Test
    fun 마지막_재고_1개에_10스레드가_몰리면_정확히_1건만_성공한다() {
        val productId = 10_201L
        seed(productId, listOf(1, 0, 0, 0, 0, 0, 0, 0))
        val successes = AtomicInteger()
        val insufficient = AtomicInteger()

        val errors = runConcurrently(10) {
            try {
                commandService.reserveStock(productId, 1)
                successes.incrementAndGet()
            } catch (e: InventoryInsufficientStockException) {
                insufficient.incrementAndGet()
            }
        }

        assertThat(errors).isEmpty()
        assertThat(successes.get()).isEqualTo(1)
        assertThat(insufficient.get()).isEqualTo(9)
        assertConserved(productId, 1)
    }

    @Test
    fun 모든_버킷이_다른_트랜잭션에_잠겨_있으면_한_행을_기다렸다가_차감하고_재시작하지_않는다() {
        val productId = 10_301L
        seed(productId, List(8) { 10 })
        val busyBefore = count("inventory.reserve.bucket_busy")
        val waitBefore = count("inventory.reserve.wait")
        val restartBefore = count("inventory.reserve.restart")

        val result = runWhileHolding(
            { connection ->
                connection.prepareStatement("SELECT stock_quantity FROM inventory_buckets WHERE product_id = ? FOR UPDATE").use {
                    it.setLong(1, productId)
                    it.executeQuery().use { rows ->
                        var locked = 0
                        while (rows.next()) locked++
                        assertThat(locked).isEqualTo(8)
                    }
                }
            },
            "bucket 예약",
            "inventory_buckets"
        ) {
            commandService.reserveStock(productId, 1)
        }

        assertThat(result.isSuccess).isTrue()
        assertThat(count("inventory.reserve.bucket_busy") - busyBefore).isEqualTo(8.0)
        assertThat(count("inventory.reserve.wait") - waitBefore).isEqualTo(1.0)
        assertThat(count("inventory.reserve.restart") - restartBefore).isZero()
        assertThat(bucketStocks(productId).sum()).isEqualTo(79)
        assertConserved(productId, 80)
    }

    @Test
    fun 모든_버킷이_부족하면_기다리지_않고_재시작해_전체_잠금으로_나눠_차감한다() {
        val productId = 10_401L
        seed(productId, List(8) { 1 })
        val waitBefore = count("inventory.reserve.wait")
        val restartBefore = count("inventory.reserve.restart")
        val fallbackBefore = count("inventory.reserve.fallback")

        commandService.reserveStock(productId, 3)

        assertThat(count("inventory.reserve.wait") - waitBefore).isZero()
        assertThat(count("inventory.reserve.restart") - restartBefore).isEqualTo(1.0)
        assertThat(count("inventory.reserve.fallback") - fallbackBefore).isEqualTo(1.0)
        assertThat(bucketStocks(productId)).containsExactly(0, 0, 0, 1, 1, 1, 1, 1)
        assertThat(reservationStatuses(productId)).containsExactly("RESERVED")
        assertConserved(productId, 8)
    }

    @Test
    fun 배치_예약이_재시작되면_첫_트랜잭션의_차감은_남지_않고_전체_잠금으로_다시_차감한다() {
        val enough = 10_411L
        val scattered = 10_412L
        seed(enough, List(8) { 10 })
        seed(scattered, List(8) { 1 })
        val restartBefore = count("inventory.reserve.restart")
        val fallbackBefore = count("inventory.reserve.fallback")

        val result = commandService.batchReserveStock(listOf(enough to 2, scattered to 3))

        assertThat(result.keys).containsExactly(enough, scattered)
        assertThat(count("inventory.reserve.restart") - restartBefore).isEqualTo(1.0)
        assertThat(count("inventory.reserve.fallback") - fallbackBefore).isEqualTo(2.0)
        assertThat(bucketStocks(enough)).containsExactly(8, 10, 10, 10, 10, 10, 10, 10)
        assertThat(bucketStocks(scattered)).containsExactly(0, 0, 0, 1, 1, 1, 1, 1)
        assertThat(reservationStatuses(enough)).containsExactly("RESERVED")
        assertThat(reservationStatuses(scattered)).containsExactly("RESERVED")
        assertConserved(enough, 80)
        assertConserved(scattered, 8)
    }

    @Test
    fun 전체_잠금_재실행도_부족하면_재고_부족으로_끝나고_재고는_그대로다() {
        val productId = 10_402L
        seed(productId, List(8) { 1 })
        val restartBefore = count("inventory.reserve.restart")

        assertThatThrownBy { commandService.reserveStock(productId, 9) }
            .isInstanceOf(InventoryInsufficientStockException::class.java)

        assertThat(count("inventory.reserve.restart") - restartBefore).isEqualTo(1.0)
        assertThat(bucketStocks(productId)).containsOnly(1)
        assertThat(reservationStatuses(productId)).isEmpty()
        assertThat(heldQuantity(productId)).isZero()
    }

    @Test
    fun 배치_예약에서_한_상품이_부족하면_예약_행과_버킷_차감이_하나도_남지_않는다() {
        val enough = 10_601L
        val short = 10_602L
        seed(enough, List(8) { 10 })
        seed(short, List(8) { 1 })

        assertThatThrownBy { commandService.batchReserveStock(listOf(enough to 2, short to 9)) }
            .isInstanceOf(InventoryInsufficientStockException::class.java)

        assertThat(reservationCount()).isZero()
        assertThat(bucketStocks(enough)).containsOnly(10)
        assertThat(bucketStocks(short)).containsOnly(1)
    }

    @Test
    fun 예약이_만료되면_만료_잡_한_주기_뒤_버킷_합이_원래대로_돌아오고_예약은_EXPIRED가_된다() {
        val productId = 10_701L
        seed(productId, List(8) { 10 })
        val reservationId = commandService.reserveStock(productId, 3)
        assertThat(bucketStocks(productId).sum()).isEqualTo(77)
        jdbcTemplate.update(
            "UPDATE inventory_reservations SET expires_at = ? WHERE reservation_id = ?",
            LocalDateTime.now().minusMinutes(5), reservationId
        )

        BucketReservationExpiryJob(
            reservationRepository, bucketStockService, transactionManager, SimpleMeterRegistry(), true, 1000
        ).expireReservations()

        assertThat(bucketStocks(productId).sum()).isEqualTo(80)
        assertThat(reservationStatuses(productId)).containsExactly("EXPIRED")
        assertThat(bucketStocks(productId)).allMatch { it >= 0 }
    }

    @Test
    fun 재고_조회는_버킷_합을_돌려주고_단건_배치_예약_뒤에는_줄어든_값을_돌려준다() {
        val productId = 10_801L
        seedWithInventory(productId, List(8) { 10 })

        assertThat(queryService.getStock(productId).stockQuantity).isEqualTo(80)

        commandService.reserveStock(productId, 3)

        assertThat(queryService.getStock(productId).stockQuantity).isEqualTo(77)

        commandService.batchReserveStock(listOf(productId to 2))

        assertThat(queryService.getStock(productId).stockQuantity).isEqualTo(75)
        assertThat(inventoryRowStock(productId)).isEqualTo(80)
    }

    @Test
    fun 재고_초기화_변경_직접_차감과_증가가_버킷_합에_반영된다() {
        val productId = productRepository.saveAndFlush(
            Product.create(
                sellerId = 1L,
                categoryId = 1L,
                name = "bucket",
                description = "bucket",
                price = BigDecimal("1000"),
                status = ProductStatus.AVAILABLE
            )
        ).id!!
        createdProductIds.add(productId)

        commandService.initStock(InventoryInitRequest(productId = productId, stockQuantity = 83))

        assertThat(bucketStocks(productId)).containsExactly(13, 10, 10, 10, 10, 10, 10, 10)
        assertThat(inventoryRowStock(productId)).isEqualTo(83)

        commandService.updateStock(productId, InventoryUpdateRequest(stockQuantity = 40))

        assertThat(bucketStocks(productId)).containsOnly(5)
        assertThat(inventoryRowStock(productId)).isEqualTo(40)

        commandService.decreaseStock(productId, 7)

        assertThat(bucketStocks(productId).sum()).isEqualTo(33)
        assertThat(bucketStocks(productId)).allMatch { it >= 0 }

        commandService.increaseStock(productId, 5)

        assertThat(bucketStocks(productId).sum()).isEqualTo(38)
        assertThat(inventoryRowStock(productId)).isEqualTo(40)
        assertThat(queryService.getStock(productId).stockQuantity).isEqualTo(38)
    }

    private fun seed(productId: Long, buckets: List<Int>) {
        buckets.forEachIndexed { bucketNo, stock ->
            jdbcTemplate.update(
                "INSERT INTO inventory_buckets (product_id, bucket_no, stock_quantity) VALUES (?, ?, ?)",
                productId, bucketNo, stock
            )
        }
    }

    private fun seedWithInventory(productId: Long, buckets: List<Int>) {
        inventoryRepository.saveAndFlush(Inventory.create(productId = productId, stockQuantity = buckets.sum()))
        seed(productId, buckets)
    }

    private fun bucketStocks(productId: Long): List<Int> =
        jdbcTemplate.queryForList(
            "SELECT stock_quantity FROM inventory_buckets WHERE product_id = ? ORDER BY bucket_no",
            Int::class.java, productId
        )

    private fun inventoryRowStock(productId: Long): Int =
        jdbcTemplate.queryForObject("SELECT stock_quantity FROM inventories WHERE product_id = ?", Int::class.java, productId)!!

    private fun heldQuantity(productId: Long): Long =
        jdbcTemplate.queryForObject(
            "SELECT COALESCE(SUM(quantity), 0) FROM inventory_reservations WHERE product_id = ? AND status IN ('RESERVED', 'CONFIRMED')",
            Long::class.java, productId
        )!!

    private fun reservationStatuses(productId: Long): List<String> =
        jdbcTemplate.queryForList(
            "SELECT status FROM inventory_reservations WHERE product_id = ? ORDER BY id",
            String::class.java, productId
        )

    private fun reservationCount(): Long =
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM inventory_reservations", Long::class.java)!!

    private fun deadlocks(): Long =
        jdbcTemplate.queryForObject(
            "SELECT `COUNT` FROM information_schema.INNODB_METRICS WHERE NAME = 'lock_deadlocks'",
            Long::class.java
        )!!

    private fun count(name: String): Double = meterRegistry.counter(name).count()

    private fun assertConserved(productId: Long, initial: Int) {
        val stocks = bucketStocks(productId)
        assertThat(stocks).allMatch { it >= 0 }
        assertThat(initial - stocks.sum().toLong()).isEqualTo(heldQuantity(productId))
    }

    private fun runConcurrently(threads: Int, task: (Int) -> Unit): List<Throwable> {
        val pool = Executors.newFixedThreadPool(threads)
        val ready = CountDownLatch(threads)
        val start = CountDownLatch(1)
        val errors = Collections.synchronizedList(mutableListOf<Throwable>())
        repeat(threads) { index ->
            pool.submit {
                ready.countDown()
                start.await()
                try {
                    task(index)
                } catch (e: Throwable) {
                    errors.add(e)
                }
            }
        }
        ready.await()
        start.countDown()
        pool.shutdown()
        assertThat(pool.awaitTermination(120, TimeUnit.SECONDS)).isTrue()
        return errors
    }
}
