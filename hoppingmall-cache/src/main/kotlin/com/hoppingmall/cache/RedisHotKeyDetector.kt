package com.hoppingmall.cache

import org.redisson.api.RScoredSortedSet
import org.redisson.api.RedissonClient
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.LongAdder

class RedisHotKeyDetector(
    private val cacheName: String,
    private val threshold: Long,
    private val windowMs: Long,
    private val redissonClient: RedissonClient,
    scheduler: ScheduledExecutorService,
    flushIntervalMs: Long = minOf(5000L, windowMs / 2)
) : HotKeyDetector {

    private val log = LoggerFactory.getLogger(RedisHotKeyDetector::class.java)

    private val localCounts = ConcurrentHashMap<String, LongAdder>()
    private val hotKeysSnapshot = AtomicReference<Set<String>>(emptySet())
    private val lastFlushEpoch = AtomicLong(-1L)

    init {
        scheduler.scheduleWithFixedDelay(::flush, flushIntervalMs, flushIntervalMs, TimeUnit.MILLISECONDS)
    }

    override fun recordAccess(key: String) {
        val adder = localCounts.computeIfAbsent(key) { LongAdder() }
        adder.increment()
    }

    override fun isHot(key: String): Boolean = hotKeysSnapshot.get().contains(key)

    internal fun flush() {
        val currentEpoch = System.currentTimeMillis() / windowMs
        val previousEpoch = currentEpoch - 1
        val zsetKey = zsetKeyFor(currentEpoch)
        val prevZsetKey = zsetKeyFor(previousEpoch)
        val expireSeconds = (windowMs * 2) / 1000

        val pendingCounts = drainLocalCounts()

        val lastEpoch = lastFlushEpoch.getAndSet(currentEpoch)
        if (lastEpoch != -1L && currentEpoch != lastEpoch) {
            localCounts.clear()
        }

        try {
            if (pendingCounts.isNotEmpty()) {
                val batch = redissonClient.createBatch()
                val asyncSet = batch.getScoredSortedSet<String>(zsetKey)
                for ((key, count) in pendingCounts) {
                    asyncSet.addScoreAsync(key, count.toDouble())
                }
                asyncSet.expireAsync(Duration.ofSeconds(expireSeconds))
                batch.execute()
            }

            val currentHot = readHotKeysFrom(zsetKey)
            val previousHot = readHotKeysFrom(prevZsetKey)
            hotKeysSnapshot.set(currentHot.union(previousHot))
        } catch (e: Exception) {
            log.warn("Redis hot-key flush failed [cache={}]: {}", cacheName, e.message)
        }
    }

    private fun readHotKeysFrom(zsetKey: String): Set<String> {
        return try {
            val scoredSet: RScoredSortedSet<String> = redissonClient.getScoredSortedSet(zsetKey)
            scoredSet.valueRange(threshold.toDouble(), true, Double.MAX_VALUE, true).toSet()
        } catch (e: Exception) {
            log.warn("Redis hot-key snapshot read failed [cache={}, key={}]: {}", cacheName, zsetKey, e.message)
            emptySet()
        }
    }

    private fun drainLocalCounts(): Map<String, Long> {
        val snapshot = mutableMapOf<String, Long>()
        for ((key, adder) in localCounts.entries) {
            val count = adder.sumThenReset()
            if (count > 0) {
                snapshot[key] = count
            }
        }
        return snapshot
    }

    private fun zsetKeyFor(epoch: Long): String = "hotkey:$cacheName:counts:$epoch"

    override fun close() {
    }
}
