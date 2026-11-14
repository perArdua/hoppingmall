package com.hoppingmall.product.inventory.domain.repository

import com.hoppingmall.product.inventory.domain.InventoryBucket
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.SQLException

@Repository
class InventoryBucketLocker(
    private val jdbcTemplate: JdbcTemplate
) {

    fun lockNowait(productId: Long, bucketNo: Int): BucketLock =
        try {
            jdbcTemplate.query(LOCK_NOWAIT_SQL, { rs, _ -> rs.getInt(1) }, productId, bucketNo)
                .firstOrNull()
                ?.let { BucketLock.Acquired(it) }
                ?: BucketLock.Missing
        } catch (e: DataAccessException) {
            if (!isLockNotAvailable(e)) throw e
            BucketLock.Busy
        }

    fun lockWaiting(productId: Long, bucketNo: Int): BucketLock =
        jdbcTemplate.query(LOCK_WAIT_SQL, { rs, _ -> rs.getInt(1) }, productId, bucketNo)
            .firstOrNull()
            ?.let { BucketLock.Acquired(it) }
            ?: BucketLock.Missing

    fun lockAll(productId: Long, bucketCount: Int): List<LockedBucket> =
        (0 until bucketCount).mapNotNull { bucketNo ->
            (lockWaiting(productId, bucketNo) as? BucketLock.Acquired)?.let { LockedBucket(bucketNo, it.stockQuantity) }
        }

    fun ensureBuckets(productId: Long, bucketCount: Int): Boolean =
        !hasBuckets(productId) && createFromInventory(productId, bucketCount)

    fun createFromInventory(productId: Long, bucketCount: Int): Boolean {
        val stock = jdbcTemplate.query(LOCK_INVENTORY_SQL, { rs, _ -> rs.getInt(1) }, productId).firstOrNull() ?: return false
        if (hasBuckets(productId)) return false
        jdbcTemplate.batchUpdate(
            CREATE_SQL,
            InventoryBucket.split(productId, stock, bucketCount).map { arrayOf<Any>(it.productId, it.bucketNo, it.stockQuantity) }
        )
        return true
    }

    private fun hasBuckets(productId: Long): Boolean =
        jdbcTemplate.queryForObject(COUNT_SQL, Long::class.java, productId)!! > 0

    fun increase(productId: Long, bucketNo: Int, quantity: Int): Int =
        jdbcTemplate.update(INCREASE_SQL, quantity, productId, bucketNo)

    fun setStock(productId: Long, bucketNo: Int, stockQuantity: Int): Int =
        jdbcTemplate.update(SET_STOCK_SQL, stockQuantity, productId, bucketNo)

    fun decrease(productId: Long, bucketNo: Int, quantity: Int): Int =
        jdbcTemplate.update(DECREASE_SQL, quantity, productId, bucketNo)

    private fun isLockNotAvailable(e: Throwable): Boolean =
        generateSequence(e) { it.cause }.any { it is SQLException && it.errorCode == MYSQL_LOCK_NOWAIT }

    data class LockedBucket(val bucketNo: Int, val stockQuantity: Int)

    sealed interface BucketLock {
        data class Acquired(val stockQuantity: Int) : BucketLock
        data object Busy : BucketLock
        data object Missing : BucketLock
    }

    companion object {
        const val MYSQL_LOCK_NOWAIT = 3572
        private const val LOCK_NOWAIT_SQL =
            "SELECT stock_quantity FROM inventory_buckets WHERE product_id = ? AND bucket_no = ? FOR UPDATE NOWAIT"
        private const val LOCK_WAIT_SQL =
            "SELECT stock_quantity FROM inventory_buckets WHERE product_id = ? AND bucket_no = ? FOR UPDATE"
        private const val LOCK_INVENTORY_SQL =
            "SELECT stock_quantity FROM inventories WHERE product_id = ? FOR UPDATE"
        private const val COUNT_SQL =
            "SELECT COUNT(*) FROM inventory_buckets WHERE product_id = ?"
        private const val CREATE_SQL =
            "INSERT INTO inventory_buckets (product_id, bucket_no, stock_quantity) VALUES (?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE stock_quantity = stock_quantity"
        private const val INCREASE_SQL =
            "UPDATE inventory_buckets SET stock_quantity = stock_quantity + ? WHERE product_id = ? AND bucket_no = ?"
        private const val SET_STOCK_SQL =
            "UPDATE inventory_buckets SET stock_quantity = ? WHERE product_id = ? AND bucket_no = ?"
        private const val DECREASE_SQL =
            "UPDATE inventory_buckets SET stock_quantity = stock_quantity - ? WHERE product_id = ? AND bucket_no = ?"
    }
}
