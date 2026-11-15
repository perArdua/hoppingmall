package com.hoppingmall.product.inventory.service

import com.hoppingmall.product.inventory.domain.Inventory
import com.hoppingmall.product.inventory.domain.repository.InventoryRepository
import com.hoppingmall.product.inventory.dto.request.InventoryAdjustRequest
import com.hoppingmall.product.inventory.dto.request.InventoryUpdateRequest
import com.hoppingmall.product.inventory.exception.InventoryInsufficientStockException
import com.hoppingmall.product.inventory.exception.InventoryInvalidStockAdjustmentException
import com.hoppingmall.product.inventory.exception.InventoryStockChangedException
import com.hoppingmall.product.support.MySqlIntegrationTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import java.sql.Connection
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

abstract class InventoryStockAdjustmentScenarios : MySqlIntegrationTestSupport() {

    @Autowired
    protected lateinit var inventoryCommandService: InventoryCommandService

    @Autowired
    protected lateinit var inventoryQueryService: InventoryQueryService

    @Autowired
    protected lateinit var inventoryRepository: InventoryRepository

    protected abstract fun seedStock(productId: Long, total: Int)

    protected abstract fun committedStock(productId: Long): Int

    protected abstract fun holdOrderDecrease(connection: Connection, productId: Long, quantity: Int)

    protected abstract fun orderLockTable(): String

    @Test
    fun 판매자가_읽은_뒤_주문이_예약하면_절대값_고치기는_409로_거절되고_예약_차감이_남는다() {
        seedStock(PRODUCT_ID, 80)
        val seenBySeller = inventoryQueryService.getStock(PRODUCT_ID).stockQuantity
        inventoryCommandService.reserveStock(PRODUCT_ID, 3)

        assertThatThrownBy {
            inventoryCommandService.updateStock(PRODUCT_ID, InventoryUpdateRequest(stockQuantity = 100, expectedStockQuantity = seenBySeller))
        }.isInstanceOf(InventoryStockChangedException::class.java)

        assertThat(committedStock(PRODUCT_ID)).isEqualTo(77)
    }

    @Test
    fun 읽은_값이_지금_값과_같으면_절대값으로_고친다() {
        seedStock(PRODUCT_ID, 80)
        inventoryCommandService.reserveStock(PRODUCT_ID, 3)

        val response = inventoryCommandService.updateStock(PRODUCT_ID, InventoryUpdateRequest(stockQuantity = 100, expectedStockQuantity = 77))

        assertThat(response.stockQuantity).isEqualTo(100)
        assertThat(committedStock(PRODUCT_ID)).isEqualTo(100)
        assertThat(inventoryQueryService.getStock(PRODUCT_ID).stockQuantity).isEqualTo(100)
    }

    @Test
    fun 증감은_그_사이의_주문_예약을_지우지_않는다() {
        seedStock(PRODUCT_ID, 80)
        inventoryCommandService.reserveStock(PRODUCT_ID, 3)

        val increased = inventoryCommandService.adjustStock(PRODUCT_ID, InventoryAdjustRequest(delta = 10))
        val decreased = inventoryCommandService.adjustStock(PRODUCT_ID, InventoryAdjustRequest(delta = -7))

        assertThat(increased.stockQuantity).isEqualTo(87)
        assertThat(decreased.stockQuantity).isEqualTo(80)
        assertThat(committedStock(PRODUCT_ID)).isEqualTo(80)
    }

    @Test
    fun 실제_주문_예약과_증감이_동시에_들어와도_둘_다_반영된다() {
        seedStock(PRODUCT_ID, 80)
        val start = CountDownLatch(1)
        val reserves = (1..CONCURRENT_CALLS).map {
            executor.submit(Callable { start.await(); inventoryCommandService.reserveStock(PRODUCT_ID, 1) })
        }
        val adjustments = (1..CONCURRENT_CALLS).map {
            executor.submit(Callable { start.await(); inventoryCommandService.adjustStock(PRODUCT_ID, InventoryAdjustRequest(delta = 2)) })
        }

        start.countDown()
        reserves.forEach { it.get(30, TimeUnit.SECONDS) }
        adjustments.forEach { it.get(30, TimeUnit.SECONDS) }

        assertThat(committedStock(PRODUCT_ID)).isEqualTo(80 - CONCURRENT_CALLS + CONCURRENT_CALLS * 2)
        assertThat(reservationCount(PRODUCT_ID)).isEqualTo(CONCURRENT_CALLS.toLong())
    }

    @Test
    fun 주문_차감이_재고를_잡은_동안_들어온_절대값_고치기는_차감을_본_뒤_409로_거절된다() {
        seedStock(PRODUCT_ID, 80)

        val result = runWhileHolding(
            holdingChanges = { holdOrderDecrease(it, PRODUCT_ID, 3) },
            actionName = "절대값 고치기",
            waitTable = orderLockTable()
        ) {
            inventoryCommandService.updateStock(PRODUCT_ID, InventoryUpdateRequest(stockQuantity = 100, expectedStockQuantity = 80))
        }

        assertThat(result.exceptionOrNull()).isInstanceOf(InventoryStockChangedException::class.java)
        assertThat(committedStock(PRODUCT_ID)).isEqualTo(77)
    }

    @Test
    fun 주문_차감이_재고를_잡은_동안_들어온_증감은_차감을_지우지_않는다() {
        seedStock(PRODUCT_ID, 80)

        val result = runWhileHolding(
            holdingChanges = { holdOrderDecrease(it, PRODUCT_ID, 3) },
            actionName = "증감",
            waitTable = orderLockTable()
        ) {
            inventoryCommandService.adjustStock(PRODUCT_ID, InventoryAdjustRequest(delta = 10))
        }

        assertThat(result.exceptionOrNull()).isNull()
        assertThat(committedStock(PRODUCT_ID)).isEqualTo(87)
    }

    @Test
    fun 증감으로_지금_재고보다_많이_빼면_거절하고_그대로_둔다() {
        seedStock(PRODUCT_ID, 8)

        assertThatThrownBy { inventoryCommandService.adjustStock(PRODUCT_ID, InventoryAdjustRequest(delta = -9)) }
            .isInstanceOf(InventoryInsufficientStockException::class.java)

        assertThat(committedStock(PRODUCT_ID)).isEqualTo(8)
    }

    @Test
    fun 증감이_0이면_거절한다() {
        seedStock(PRODUCT_ID, 80)

        assertThatThrownBy { inventoryCommandService.adjustStock(PRODUCT_ID, InventoryAdjustRequest(delta = 0)) }
            .isInstanceOf(InventoryInvalidStockAdjustmentException::class.java)

        assertThat(committedStock(PRODUCT_ID)).isEqualTo(80)
    }

    @Test
    fun 증감_뒤_재고를_조회하면_바뀐_수량이_보인다() {
        seedStock(PRODUCT_ID, 80)
        assertThat(inventoryQueryService.getStock(PRODUCT_ID).stockQuantity).isEqualTo(80)

        inventoryCommandService.adjustStock(PRODUCT_ID, InventoryAdjustRequest(delta = 5))

        assertThat(inventoryQueryService.getStock(PRODUCT_ID).stockQuantity).isEqualTo(85)
    }

    private fun reservationCount(productId: Long): Long =
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM inventory_reservations WHERE product_id = ?", Long::class.java, productId)!!

    protected companion object {
        const val PRODUCT_ID = 97_300L
        const val CONCURRENT_CALLS = 8
    }
}

@DisplayName("재고 증감·읽은 값 확인 고치기 MySQL 통합 테스트 (single 모드)")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class SingleInventoryStockAdjustmentMySqlIntegrationTest : InventoryStockAdjustmentScenarios() {

    override fun seedStock(productId: Long, total: Int) {
        inventoryRepository.saveAndFlush(Inventory.create(productId = productId, stockQuantity = total))
    }

    override fun committedStock(productId: Long): Int =
        jdbcTemplate.queryForObject("SELECT stock_quantity FROM inventories WHERE product_id = ?", Int::class.java, productId)!!

    override fun holdOrderDecrease(connection: Connection, productId: Long, quantity: Int) {
        connection.prepareStatement("UPDATE inventories SET stock_quantity = stock_quantity - ? WHERE product_id = ?").use {
            it.setInt(1, quantity)
            it.setLong(2, productId)
            it.executeUpdate()
        }
    }

    override fun orderLockTable(): String = "inventories"
}

@DisplayName("재고 증감·읽은 값 확인 고치기 MySQL 통합 테스트 (bucket 모드)")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@TestPropertySource(properties = ["inventory.mode=bucket", "inventory.bucket.count=8", "inventory.semaphore.permits=64"])
class BucketInventoryStockAdjustmentMySqlIntegrationTest : InventoryStockAdjustmentScenarios() {

    override fun seedStock(productId: Long, total: Int) {
        inventoryRepository.saveAndFlush(Inventory.create(productId = productId, stockQuantity = total))
        (0 until 8).forEach { bucketNo ->
            jdbcTemplate.update(
                "INSERT INTO inventory_buckets (product_id, bucket_no, stock_quantity) VALUES (?, ?, ?)",
                productId, bucketNo, total / 8
            )
        }
    }

    override fun committedStock(productId: Long): Int =
        jdbcTemplate.queryForObject("SELECT SUM(stock_quantity) FROM inventory_buckets WHERE product_id = ?", Int::class.java, productId)!!

    override fun holdOrderDecrease(connection: Connection, productId: Long, quantity: Int) {
        connection.prepareStatement("UPDATE inventory_buckets SET stock_quantity = stock_quantity - ? WHERE product_id = ? AND bucket_no = 0").use {
            it.setInt(1, quantity)
            it.setLong(2, productId)
            it.executeUpdate()
        }
    }

    override fun orderLockTable(): String = "inventory_buckets"
}
