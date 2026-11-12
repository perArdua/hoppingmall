package com.hoppingmall.product.inventory.service

import com.hoppingmall.product.inventory.domain.InventoryBucket
import com.hoppingmall.product.inventory.domain.repository.InventoryBucketLocker
import com.hoppingmall.product.inventory.domain.repository.InventoryBucketLocker.BucketLock
import com.hoppingmall.product.inventory.domain.repository.InventoryBucketLocker.LockedBucket
import com.hoppingmall.product.inventory.domain.repository.InventoryBucketRepository
import com.hoppingmall.product.inventory.exception.InventoryInsufficientStockException
import com.hoppingmall.product.inventory.exception.InventoryNotFoundException
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.DistributionSummary
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class BucketStockService(
    private val inventoryBucketRepository: InventoryBucketRepository,
    private val inventoryBucketLocker: InventoryBucketLocker,
    meterRegistry: MeterRegistry,
    @Value("\${inventory.mode:single}") mode: String,
    @Value("\${inventory.bucket.count:8}") val bucketCount: Int
) {
    val enabled: Boolean = mode.equals(MODE_BUCKET, ignoreCase = true)

    private val bucketAttempts = DistributionSummary.builder("inventory.reserve.bucket_attempts")
        .description("Bucket lock attempts per reserve before success or fallback")
        .register(meterRegistry)

    private val fallbackCounter = Counter.builder("inventory.reserve.fallback")
        .description("Reserves that took stock while locking all buckets")
        .register(meterRegistry)

    private val waitCounter = Counter.builder("inventory.reserve.wait")
        .description("Reserves that found every bucket busy and waited on one bucket")
        .register(meterRegistry)

    private val restartCounter = Counter.builder("inventory.reserve.restart")
        .description("Reserve transactions rolled back after a bucket walk and rerun locking all buckets")
        .register(meterRegistry)

    private val insufficientCounter = Counter.builder("inventory.reserve.rejected")
        .tag("reason", "insufficient")
        .register(meterRegistry)

    private val busyCounter = Counter.builder("inventory.reserve.bucket_busy")
        .description("Bucket locks skipped because another transaction held them (NOWAIT)")
        .register(meterRegistry)

    init {
        require(bucketCount > 0) { "inventory.bucket.count must be positive" }
        require(enabled || mode.equals(MODE_SINGLE, ignoreCase = true)) { "inventory.mode must be single or bucket: '$mode'" }
    }

    fun initBuckets(productId: Long, total: Int) {
        inventoryBucketRepository.saveAll(InventoryBucket.split(productId, total, bucketCount))
    }

    fun reserve(productId: Long, quantity: Int, key: String) {
        val start = Math.floorMod(key.hashCode(), bucketCount)
        var holdsShortBucket = false
        for (attempt in 0 until bucketCount) {
            val bucketNo = (start + attempt) % bucketCount
            when (tryTake(productId, bucketNo, quantity)) {
                TakeResult.TAKEN -> {
                    bucketAttempts.record((attempt + 1).toDouble())
                    return
                }
                TakeResult.SHORT -> holdsShortBucket = true
                TakeResult.BUSY -> Unit
            }
        }
        bucketAttempts.record(bucketCount.toDouble())
        if (holdsShortBucket) throw BucketWalkExhaustedException(productId)
        waitCounter.increment()
        ReserveDeadline.check()
        val lock = inventoryBucketLocker.lockWaiting(productId, start)
        if (lock is BucketLock.Acquired && lock.stockQuantity >= quantity &&
            inventoryBucketLocker.decrease(productId, start, quantity) > 0
        ) {
            return
        }
        throw BucketWalkExhaustedException(productId)
    }

    fun reserveLockingAll(productId: Long, quantity: Int) {
        fallbackCounter.increment()
        ReserveDeadline.check()
        takeAcrossBuckets(productId, quantity)
    }

    fun decrease(productId: Long, quantity: Int) {
        takeAcrossBuckets(productId, quantity)
    }

    fun recordRestart() {
        restartCounter.increment()
    }

    fun restore(productId: Long, quantity: Int) {
        if (quantity <= 0) return
        val smallest = lockAll(productId).minWith(compareBy({ it.stockQuantity }, { it.bucketNo }))
        inventoryBucketLocker.increase(productId, smallest.bucketNo, quantity)
    }

    fun redistribute(productId: Long, total: Int) {
        val buckets = lockAll(productId)
        buckets.forEach {
            inventoryBucketLocker.setStock(productId, it.bucketNo, InventoryBucket.shareOf(total, buckets.size, it.bucketNo))
        }
    }

    fun totalStock(productId: Long): Int = inventoryBucketRepository.sumStock(productId).toInt()

    fun totalStocks(productIds: List<Long>): Map<Long, Int> {
        if (productIds.isEmpty()) return emptyMap()
        return inventoryBucketRepository.sumStockByProductIds(productIds)
            .associate { (it[0] as Number).toLong() to (it[1] as Number).toInt() }
    }

    fun hasBuckets(productId: Long): Boolean = inventoryBucketRepository.countByProductId(productId) > 0

    private fun tryTake(productId: Long, bucketNo: Int, quantity: Int): TakeResult =
        when (val lock = inventoryBucketLocker.lockNowait(productId, bucketNo)) {
            is BucketLock.Acquired -> when {
                lock.stockQuantity < quantity -> TakeResult.SHORT
                inventoryBucketLocker.decrease(productId, bucketNo, quantity) > 0 -> TakeResult.TAKEN
                else -> TakeResult.SHORT
            }
            BucketLock.Busy -> {
                busyCounter.increment()
                TakeResult.BUSY
            }
            BucketLock.Missing -> TakeResult.SHORT
        }

    private enum class TakeResult { TAKEN, SHORT, BUSY }

    private fun lockAll(productId: Long): List<LockedBucket> =
        inventoryBucketLocker.lockAll(productId, bucketCount).ifEmpty { throw InventoryNotFoundException() }

    private fun takeAcrossBuckets(productId: Long, quantity: Int) {
        val buckets = lockAll(productId)
        if (buckets.sumOf { it.stockQuantity.toLong() } < quantity) {
            insufficientCounter.increment()
            throw InventoryInsufficientStockException()
        }
        var remaining = quantity
        for (bucket in buckets) {
            val taken = minOf(bucket.stockQuantity, remaining)
            if (taken > 0) inventoryBucketLocker.decrease(productId, bucket.bucketNo, taken)
            remaining -= taken
            if (remaining == 0) break
        }
    }

    companion object {
        const val MODE_SINGLE = "single"
        const val MODE_BUCKET = "bucket"
    }
}
