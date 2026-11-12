package com.hoppingmall.product.inventory.domain.repository

import com.hoppingmall.product.inventory.domain.repository.InventoryBucketLocker.BucketLock
import com.hoppingmall.product.inventory.service.BucketStockService
import com.hoppingmall.product.inventory.service.BucketWalkExhaustedException
import com.hoppingmall.product.support.MySqlIntegrationTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.support.TransactionTemplate
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@DisplayName("재고 버킷 잠금 MySQL 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class InventoryBucketLockerMySqlIntegrationTest : MySqlIntegrationTestSupport() {

    @Autowired
    private lateinit var inventoryBucketLocker: InventoryBucketLocker

    @Autowired
    private lateinit var bucketStockService: BucketStockService

    @Autowired
    private lateinit var transactionTemplate: TransactionTemplate

    @Test
    fun 다른_트랜잭션이_잠근_버킷은_기다리지_않고_잠겨_있음으로_돌아오고_트랜잭션은_계속_쓸_수_있다() {
        initBuckets(PRODUCT_ID, 80)

        dataSource.connection.use { holder ->
            holder.autoCommit = false
            try {
                holder.prepareStatement("SELECT stock_quantity FROM inventory_buckets WHERE product_id = ? AND bucket_no = 0 FOR UPDATE")
                    .use { statement ->
                        statement.setLong(1, PRODUCT_ID)
                        statement.executeQuery().use { assertThat(it.next()).isTrue() }
                    }

                val rollbackOnly = transactionTemplate.execute { status ->
                    assertThat(inventoryBucketLocker.lockNowait(PRODUCT_ID, 0)).isEqualTo(BucketLock.Busy)
                    assertThat(inventoryBucketLocker.lockNowait(PRODUCT_ID, 1)).isEqualTo(BucketLock.Acquired(10))
                    assertThat(inventoryBucketLocker.decrease(PRODUCT_ID, 1, 4)).isEqualTo(1)
                    status.isRollbackOnly
                }

                assertThat(rollbackOnly).isFalse()
            } finally {
                holder.rollback()
                holder.autoCommit = true
            }
        }

        assertThat(bucketStock(PRODUCT_ID, 0)).isEqualTo(10)
        assertThat(bucketStock(PRODUCT_ID, 1)).isEqualTo(6)
    }

    @Test
    fun 없는_버킷_행은_잠금_시도에서_없음으로_돌아온다() {
        val lock = transactionTemplate.execute { inventoryBucketLocker.lockNowait(PRODUCT_ID, 0) }

        assertThat(lock).isEqualTo(BucketLock.Missing)
    }

    @Test
    fun 동시_예약과_복원을_거쳐도_버킷_수량_합이_보존된다() {
        initBuckets(PRODUCT_ID, 100)
        val threads = 16
        val errors = Collections.synchronizedList(mutableListOf<Throwable>())

        runTogether(threads) { index ->
            runCatching { reserveWithRestart(PRODUCT_ID, 3, "key-$index") }.onFailure { errors.add(it) }
        }

        assertThat(errors).isEmpty()
        assertThat(bucketTotal(PRODUCT_ID)).isEqualTo(100 - threads * 3)
        assertThat(minBucketStock(PRODUCT_ID)).isGreaterThanOrEqualTo(0)

        runTogether(threads) {
            runCatching { transactionTemplate.execute { bucketStockService.restore(PRODUCT_ID, 3) } }.onFailure { errors.add(it) }
        }

        assertThat(errors).isEmpty()
        assertThat(bucketTotal(PRODUCT_ID)).isEqualTo(100)
        assertThat(bucketStockService.totalStock(PRODUCT_ID)).isEqualTo(100)
    }

    @Test
    fun 전체_잠금은_그_상품의_버킷_행만_잠근다() {
        initBuckets(PRODUCT_ID, 80)
        initBuckets(OTHER_PRODUCT_ID, 80)
        val bucketCount = bucketStockService.bucketCount
        val locked = CountDownLatch(1)
        val release = CountDownLatch(1)

        val holder = executor.submit<Int> {
            transactionTemplate.execute {
                val buckets = inventoryBucketLocker.lockAll(PRODUCT_ID, bucketCount)
                locked.countDown()
                release.await(30, TimeUnit.SECONDS)
                buckets.size
            }
        }
        try {
            assertThat(locked.await(30, TimeUnit.SECONDS)).isTrue()

            val lockModes = jdbcTemplate.queryForList(
                """
                SELECT LOCK_MODE FROM performance_schema.data_locks
                WHERE OBJECT_SCHEMA = DATABASE() AND OBJECT_NAME = 'inventory_buckets' AND LOCK_TYPE = 'RECORD'
                """.trimIndent(),
                String::class.java
            )
            assertThat(lockModes).hasSize(bucketCount).containsOnly("X,REC_NOT_GAP")

            transactionTemplate.execute {
                (0 until bucketCount).forEach { bucketNo ->
                    assertThat(inventoryBucketLocker.lockNowait(OTHER_PRODUCT_ID, bucketNo)).isEqualTo(BucketLock.Acquired(10))
                }
                assertThat(inventoryBucketLocker.lockNowait(PRODUCT_ID, 0)).isEqualTo(BucketLock.Busy)
            }
        } finally {
            release.countDown()
        }

        assertThat(holder.get(30, TimeUnit.SECONDS)).isEqualTo(bucketCount)
    }

    @Test
    fun 기다리는_잠금은_먼저_잡은_트랜잭션이_끝난_뒤의_수량을_읽는다() {
        initBuckets(PRODUCT_ID, 80)
        val seen = Collections.synchronizedList(mutableListOf<BucketLock>())

        val result = runWhileHolding(
            { connection ->
                connection.prepareStatement("UPDATE inventory_buckets SET stock_quantity = stock_quantity - 7 WHERE product_id = ? AND bucket_no = 2")
                    .use { statement ->
                        statement.setLong(1, PRODUCT_ID)
                        assertThat(statement.executeUpdate()).isEqualTo(1)
                    }
            },
            "버킷 대기 잠금",
            "inventory_buckets"
        ) {
            seen.add(transactionTemplate.execute { inventoryBucketLocker.lockWaiting(PRODUCT_ID, 2) }!!)
        }

        assertThat(result.isSuccess).isTrue()
        assertThat(seen).containsExactly(BucketLock.Acquired(3))
    }

    private fun initBuckets(productId: Long, total: Int) {
        transactionTemplate.execute { bucketStockService.initBuckets(productId, total) }
    }

    private fun reserveWithRestart(productId: Long, quantity: Int, key: String) {
        try {
            transactionTemplate.execute { bucketStockService.reserve(productId, quantity, key) }
        } catch (e: BucketWalkExhaustedException) {
            transactionTemplate.execute { bucketStockService.reserveLockingAll(productId, quantity) }
        }
    }

    private fun runTogether(threads: Int, task: (Int) -> Unit) {
        val start = CountDownLatch(1)
        val futures = (0 until threads).map { index ->
            executor.submit {
                start.await()
                task(index)
            }
        }
        start.countDown()
        futures.forEach { it.get(60, TimeUnit.SECONDS) }
    }

    private fun bucketStock(productId: Long, bucketNo: Int): Int =
        jdbcTemplate.queryForObject(
            "SELECT stock_quantity FROM inventory_buckets WHERE product_id = ? AND bucket_no = ?", Int::class.java, productId, bucketNo
        )!!

    private fun bucketTotal(productId: Long): Int =
        jdbcTemplate.queryForObject("SELECT SUM(stock_quantity) FROM inventory_buckets WHERE product_id = ?", Int::class.java, productId)!!

    private fun minBucketStock(productId: Long): Int =
        jdbcTemplate.queryForObject("SELECT MIN(stock_quantity) FROM inventory_buckets WHERE product_id = ?", Int::class.java, productId)!!

    companion object {
        private const val PRODUCT_ID = 3001L
        private const val OTHER_PRODUCT_ID = 3002L
    }
}
