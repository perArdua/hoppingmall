package com.hoppingmall.product.inventory.service

import com.hoppingmall.product.inventory.domain.Inventory
import com.hoppingmall.product.inventory.domain.repository.InventoryRepository
import com.hoppingmall.product.inventory.exception.InventoryInsufficientStockException
import com.hoppingmall.product.inventory.exception.InventoryNotFoundException
import com.hoppingmall.product.inventory.exception.code.InventoryErrorCode
import com.hoppingmall.product.support.MySqlIntegrationTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

@DisplayName("single 모드 재고 예약 MySQL 회귀 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class SingleInventoryReservationMySqlIntegrationTest : MySqlIntegrationTestSupport() {

    @Autowired
    private lateinit var inventoryCommandService: InventoryCommandService

    @Autowired
    private lateinit var bucketStockService: BucketStockService

    @Autowired
    private lateinit var inventoryRepository: InventoryRepository

    @Test
    fun 기본_설정은_single_모드이고_예약이_버킷_행을_쓰지_않는다() {
        seed(PRODUCT_A, 10)

        inventoryCommandService.reserveStock(PRODUCT_A, 3)

        assertThat(bucketStockService.enabled).isFalse()
        assertThat(stock(PRODUCT_A)).isEqualTo(7)
        assertThat(bucketRowCount()).isZero()
    }

    @Test
    fun 스레드_32개가_동시에_예약과_취소를_해도_재고와_예약_수량의_합이_보존되고_음수가_없다() {
        seed(PRODUCT_A, 150)

        val errors = runTogether(32) { thread ->
            val random = Random(thread)
            repeat(10) {
                try {
                    val reservationId = inventoryCommandService.reserveStock(PRODUCT_A, random.nextInt(1, 3))
                    if (random.nextBoolean()) inventoryCommandService.cancelReservation(reservationId)
                } catch (e: InventoryInsufficientStockException) {
                    Unit
                }
            }
        }

        assertThat(errors).isEmpty()
        assertThat(stock(PRODUCT_A)).isGreaterThanOrEqualTo(0)
        assertThat(stock(PRODUCT_A) + reservedQuantity(PRODUCT_A)).isEqualTo(150)
        assertThat(bucketRowCount()).isZero()
    }

    @Test
    fun 마지막_재고_1개에_10스레드가_몰리면_정확히_1건만_성공한다() {
        seed(PRODUCT_A, 1)
        val successes = AtomicInteger()
        val rejected = AtomicInteger()

        val errors = runTogether(10) {
            try {
                inventoryCommandService.reserveStock(PRODUCT_A, 1)
                successes.incrementAndGet()
            } catch (e: InventoryInsufficientStockException) {
                rejected.incrementAndGet()
            }
        }

        assertThat(errors).isEmpty()
        assertThat(successes.get()).isEqualTo(1)
        assertThat(rejected.get()).isEqualTo(9)
        assertThat(stock(PRODUCT_A)).isZero()
        assertThat(reservationCount(PRODUCT_A, "RESERVED")).isEqualTo(1)
    }

    @Test
    fun 배치_예약이_성공하면_상품마다_재고가_줄고_예약이_하나씩_생긴다() {
        seed(PRODUCT_A, 10)
        seed(PRODUCT_B, 20)
        seed(PRODUCT_C, 30)

        val reservations = inventoryCommandService.batchReserveStock(listOf(PRODUCT_C to 3, PRODUCT_A to 1, PRODUCT_B to 2))

        assertThat(reservations.keys).containsExactlyInAnyOrder(PRODUCT_A, PRODUCT_B, PRODUCT_C)
        assertThat(reservations.values.toSet()).hasSize(3)
        assertThat(stock(PRODUCT_A)).isEqualTo(9)
        assertThat(stock(PRODUCT_B)).isEqualTo(18)
        assertThat(stock(PRODUCT_C)).isEqualTo(27)
        listOf(PRODUCT_A to 1, PRODUCT_B to 2, PRODUCT_C to 3).forEach { (productId, quantity) ->
            assertThat(reservationRows(productId)).containsExactly(Triple(reservations.getValue(productId), quantity, "RESERVED"))
        }
    }

    @Test
    fun 배치_예약에서_한_상품의_재고가_부족하면_재고_부족_예외로_끝나고_아무것도_남지_않는다() {
        seed(PRODUCT_A, 10)
        seed(PRODUCT_B, 1)
        seed(PRODUCT_C, 30)

        val thrown = catchThrowable {
            inventoryCommandService.batchReserveStock(listOf(PRODUCT_A to 1, PRODUCT_B to 2, PRODUCT_C to 3))
        }

        assertThat(thrown).isExactlyInstanceOf(InventoryInsufficientStockException::class.java)
        assertThat((thrown as InventoryInsufficientStockException).errorCode).isEqualTo(InventoryErrorCode.INVENTORY_INSUFFICIENT_STOCK)
        assertThat(stock(PRODUCT_A)).isEqualTo(10)
        assertThat(stock(PRODUCT_B)).isEqualTo(1)
        assertThat(stock(PRODUCT_C)).isEqualTo(30)
        assertThat(reservationRowCount()).isZero()
    }

    @Test
    fun 배치_예약에서_한_상품의_재고_행이_없으면_재고_없음_예외로_끝나고_아무것도_남지_않는다() {
        seed(PRODUCT_A, 10)
        seed(PRODUCT_C, 30)

        val thrown = catchThrowable {
            inventoryCommandService.batchReserveStock(listOf(PRODUCT_A to 1, PRODUCT_B to 2, PRODUCT_C to 3))
        }

        assertThat(thrown).isExactlyInstanceOf(InventoryNotFoundException::class.java)
        assertThat((thrown as InventoryNotFoundException).errorCode).isEqualTo(InventoryErrorCode.INVENTORY_NOT_FOUND)
        assertThat(stock(PRODUCT_A)).isEqualTo(10)
        assertThat(stock(PRODUCT_C)).isEqualTo(30)
        assertThat(reservationRowCount()).isZero()
    }

    @Test
    fun 겹치는_상품을_다른_순서로_담은_배치_예약을_동시에_반복해도_교착_없이_보존식이_성립한다() {
        seed(PRODUCT_A, 200)
        seed(PRODUCT_B, 200)
        seed(PRODUCT_C, 200)
        val batches = listOf(
            listOf(PRODUCT_A to 1, PRODUCT_B to 1, PRODUCT_C to 1),
            listOf(PRODUCT_C to 1, PRODUCT_B to 1, PRODUCT_A to 1),
            listOf(PRODUCT_B to 2, PRODUCT_A to 1),
            listOf(PRODUCT_C to 2, PRODUCT_B to 1)
        )
        val deadlocksBefore = deadlockCount()
        val reserved = AtomicInteger()

        val errors = runTogether(8) { thread ->
            repeat(10) {
                inventoryCommandService.batchReserveStock(batches[thread % batches.size])
                reserved.incrementAndGet()
            }
        }

        assertThat(errors).isEmpty()
        assertThat(reserved.get()).isEqualTo(80)
        assertThat(deadlockCount()).isEqualTo(deadlocksBefore)
        listOf(PRODUCT_A to 60, PRODUCT_B to 100, PRODUCT_C to 80).forEach { (productId, expectedReserved) ->
            assertThat(reservedQuantity(productId)).isEqualTo(expectedReserved)
            assertThat(stock(productId) + reservedQuantity(productId)).isEqualTo(200)
        }
    }

    @Test
    fun 단건_예약에서_재고가_부족하면_재고_부족_예외와_INV002를_받는다() {
        seed(PRODUCT_A, 2)

        val thrown = catchThrowable { inventoryCommandService.reserveStock(PRODUCT_A, 3) }

        assertThat(thrown).isExactlyInstanceOf(InventoryInsufficientStockException::class.java)
        val errorCode = (thrown as InventoryInsufficientStockException).errorCode
        assertThat(errorCode.code).isEqualTo("INV002")
        assertThat(errorCode.status).isEqualTo(HttpStatus.BAD_REQUEST)
        assertThat(stock(PRODUCT_A)).isEqualTo(2)
        assertThat(reservationRowCount()).isZero()
    }

    @Test
    fun 단건_예약에서_재고_행이_없으면_재고_없음_예외와_INV001을_받는다() {
        val thrown = catchThrowable { inventoryCommandService.reserveStock(PRODUCT_A, 1) }

        assertThat(thrown).isExactlyInstanceOf(InventoryNotFoundException::class.java)
        val errorCode = (thrown as InventoryNotFoundException).errorCode
        assertThat(errorCode.code).isEqualTo("INV001")
        assertThat(errorCode.status).isEqualTo(HttpStatus.NOT_FOUND)
        assertThat(reservationRowCount()).isZero()
    }

    @Test
    fun 확정된_예약은_단건_취소로_취소되지_않고_재고가_그대로다() {
        seed(PRODUCT_A, 10)
        val reservationId = inventoryCommandService.reserveStock(PRODUCT_A, 3)
        assertThat(inventoryCommandService.confirmReservations(listOf(reservationId))).isTrue()

        inventoryCommandService.cancelReservation(reservationId)

        assertThat(reservationRows(PRODUCT_A)).containsExactly(Triple(reservationId, 3, "CONFIRMED"))
        assertThat(stock(PRODUCT_A)).isEqualTo(7)
    }

    @Test
    fun 확정된_예약은_배치_취소로_취소되지_않고_재고가_그대로다() {
        seed(PRODUCT_A, 10)
        seed(PRODUCT_B, 10)
        val reservations = inventoryCommandService.batchReserveStock(listOf(PRODUCT_A to 3, PRODUCT_B to 4))
        assertThat(inventoryCommandService.confirmReservations(reservations.values.toList())).isTrue()

        inventoryCommandService.cancelReservations(reservations.values.toList())

        assertThat(reservationRows(PRODUCT_A)).containsExactly(Triple(reservations.getValue(PRODUCT_A), 3, "CONFIRMED"))
        assertThat(reservationRows(PRODUCT_B)).containsExactly(Triple(reservations.getValue(PRODUCT_B), 4, "CONFIRMED"))
        assertThat(stock(PRODUCT_A)).isEqualTo(7)
        assertThat(stock(PRODUCT_B)).isEqualTo(6)
    }

    private fun seed(productId: Long, stockQuantity: Int) {
        inventoryRepository.saveAndFlush(Inventory.create(productId = productId, stockQuantity = stockQuantity))
    }

    private fun runTogether(threads: Int, task: (Int) -> Unit): List<Throwable> {
        val start = CountDownLatch(1)
        val errors = Collections.synchronizedList(mutableListOf<Throwable>())
        val futures = (0 until threads).map { index ->
            executor.submit {
                start.await()
                try {
                    task(index)
                } catch (e: Throwable) {
                    errors.add(e)
                }
            }
        }
        start.countDown()
        futures.forEach { it.get(120, TimeUnit.SECONDS) }
        return errors
    }

    private fun stock(productId: Long): Int =
        jdbcTemplate.queryForObject("SELECT stock_quantity FROM inventories WHERE product_id = ?", Int::class.java, productId)!!

    private fun reservedQuantity(productId: Long): Int =
        jdbcTemplate.queryForObject(
            "SELECT COALESCE(SUM(quantity), 0) FROM inventory_reservations WHERE product_id = ? AND status = 'RESERVED'",
            Int::class.java,
            productId
        )!!

    private fun reservationCount(productId: Long, status: String): Int =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM inventory_reservations WHERE product_id = ? AND status = ?", Int::class.java, productId, status
        )!!

    private fun reservationRowCount(): Int =
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM inventory_reservations", Int::class.java)!!

    private fun reservationRows(productId: Long): List<Triple<String, Int, String>> =
        jdbcTemplate.query(
            "SELECT reservation_id, quantity, status FROM inventory_reservations WHERE product_id = ? ORDER BY id",
            { rs, _ -> Triple(rs.getString(1), rs.getInt(2), rs.getString(3)) },
            productId
        )

    private fun bucketRowCount(): Int =
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM inventory_buckets", Int::class.java)!!

    private fun deadlockCount(): Long =
        jdbcTemplate.queryForObject(
            "SELECT `COUNT` FROM information_schema.INNODB_METRICS WHERE NAME = 'lock_deadlocks'", Long::class.java
        )!!

    companion object {
        private const val PRODUCT_A = 5001L
        private const val PRODUCT_B = 5002L
        private const val PRODUCT_C = 5003L
    }
}
