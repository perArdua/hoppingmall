package com.hoppingmall.product.inventory.service

import com.hoppingmall.product.inventory.domain.repository.InventoryReservationRepository
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Profile
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicLong

@Service
@Profile("!test")
@ConditionalOnProperty(name = ["inventory.link-check.enabled"], havingValue = "true")
class ReservationLinkCheckJob(
    private val inventoryReservationRepository: InventoryReservationRepository,
    private val bucketStockService: BucketStockService,
    private val linkPort: ReservationLinkPort,
    transactionManager: PlatformTransactionManager,
    meterRegistry: MeterRegistry,
    @Value("\${inventory.link-check.batch-size:500}") private val batchSize: Int,
    @Value("\${inventory.link-check.budget-ms:2500}") private val budgetMs: Long,
    @Value("\${inventory.link-check.floor-seconds:600}") private val floorSeconds: Long,
    @Value("\${inventory.link-check.claim-hours:24}") private val claimHours: Long
) {
    private val logger = LoggerFactory.getLogger(ReservationLinkCheckJob::class.java)
    private val transactionTemplate = TransactionTemplate(transactionManager)

    private val checked = Counter.builder("inventory.linkcheck.checked").register(meterRegistry)
    private val linkedCounter = Counter.builder("inventory.linkcheck.linked").register(meterRegistry)
    private val abortedCounter = Counter.builder("inventory.linkcheck.aborted").register(meterRegistry)
    private val floorCounter = Counter.builder("inventory.linkcheck.floor_expired").register(meterRegistry)
    private val claimedFloorCounter = Counter.builder("inventory.linkcheck.claimed_floor_expired").register(meterRegistry)
    private val failedCycles = Counter.builder("inventory.linkcheck.failed_cycles").register(meterRegistry)
    private val failedProducts = Counter.builder("inventory.linkcheck.failed_products").register(meterRegistry)
    private val cycles = Counter.builder("inventory.linkcheck.cycles").register(meterRegistry)
    private val oldestAgeMs = AtomicLong(0)

    @Autowired
    private var inventoryCacheEvictor: InventoryCacheEvictor? = null

    init {
        require(bucketStockService.enabled) { "inventory.link-check.enabled requires inventory.mode=bucket" }
        Gauge.builder("inventory.linkcheck.queue_oldest_age_seconds", oldestAgeMs) { it.get() / 1000.0 }
            .description("now - oldest expires_at among RESERVED rows past expiry")
            .register(meterRegistry)
    }

    @Scheduled(fixedDelayString = "\${inventory.link-check.interval-ms:3000}")
    fun run() {
        runCycle(LocalDateTime.now())
    }

    fun runCycle(now: LocalDateTime): CycleResult {
        cycles.increment()
        val started = System.currentTimeMillis()
        updateOldestAge(now)
        val floor = expireFloor(now)
        expireClaimedFloor(now)
        var linkedTotal = 0
        var abortedTotal = 0
        var batches = 0
        var failed = false
        val failedProductIds = mutableSetOf<Long>()
        while (System.currentTimeMillis() - started < budgetMs) {
            val candidates = collectCandidates(now, failedProductIds)
            if (candidates.isEmpty()) break
            batches++
            val result = try {
                linkPort.check(candidates.map { it.first })
            } catch (e: Exception) {
                failedCycles.increment()
                failed = true
                logger.warn("연결 확인 실패, 이번 주기 변경 없음: n={}, error={}", candidates.size, e.toString())
                break
            }
            checked.increment(candidates.size.toDouble())
            linkedTotal += claim(result.linked, now)
            abortedTotal += abort(candidates, result.aborted, failedProductIds)
            if (candidates.size < batchSize) break
        }
        return CycleResult(batches, linkedTotal, abortedTotal, floor, failed)
    }

    private fun updateOldestAge(now: LocalDateTime) {
        val oldest = inventoryReservationRepository.oldestExpiredExpiresAt(now)
        oldestAgeMs.set(if (oldest == null) 0 else Duration.between(oldest, now).toMillis())
    }

    private fun collectCandidates(now: LocalDateTime, excludedProductIds: Set<Long>): List<Pair<String, Long>> {
        val out = mutableListOf<Pair<String, Long>>()
        val products = inventoryReservationRepository.countExpiredGroupByProduct(now)
            .map { (it[0] as Number).toLong() }
            .filterNot { it in excludedProductIds }
            .sorted()
        for (productId in products) {
            val remaining = batchSize - out.size
            if (remaining <= 0) break
            inventoryReservationRepository.findExpiredReservationIds(productId, now, remaining)
                .forEach { out.add(it to productId) }
        }
        return out
    }

    private fun claim(linked: List<String>, now: LocalDateTime): Int {
        if (linked.isEmpty()) return 0
        val n = transactionTemplate.execute {
            inventoryReservationRepository.claimByReservationIds(linked, LocalDateTime.now().plusHours(claimHours), LocalDateTime.now())
        } ?: 0
        linkedCounter.increment(n.toDouble())
        return n
    }

    private fun abort(candidates: List<Pair<String, Long>>, aborted: List<String>, failedProductIds: MutableSet<Long>): Int {
        if (aborted.isEmpty()) return 0
        val abortedSet = aborted.toSet()
        val total = expireByProduct(
            candidates.filter { it.first in abortedSet }.groupBy({ it.second }, { it.first }),
            failedProductIds
        )
        abortedCounter.increment(total.toDouble())
        return total
    }

    private fun expireFloor(now: LocalDateTime): Int {
        val rows = inventoryReservationRepository.findReservedCreatedBefore(now.minusSeconds(floorSeconds), batchSize)
        if (rows.isEmpty()) return 0
        val total = expireByProduct(rows.groupBy({ (it[1] as Number).toLong() }, { it[0] as String }))
        floorCounter.increment(total.toDouble())
        return total
    }

    private fun expireClaimedFloor(now: LocalDateTime) {
        val rows = inventoryReservationRepository.findClaimedExpiredBefore(now, batchSize)
        if (rows.isEmpty()) return
        val total = expireByProduct(
            rows.groupBy({ (it[1] as Number).toLong() }, { it[0] as String }),
            expire = ::expireClaimedLocked
        )
        claimedFloorCounter.increment(total.toDouble())
        if (total > 0) logger.warn("선점 기한이 지난 CLAIMED 예약 만료: n={}", total)
    }

    private fun expireByProduct(
        reservationIdsByProduct: Map<Long, List<String>>,
        failedProductIds: MutableSet<Long> = mutableSetOf(),
        expire: (Long, List<String>) -> Int = ::expireLocked
    ): Int {
        var total = 0
        reservationIdsByProduct.toSortedMap().forEach { (productId, reservationIds) ->
            try {
                total += expire(productId, reservationIds)
            } catch (e: Exception) {
                failedProductIds.add(productId)
                failedProducts.increment()
                logger.error("링크 확인 만료 처리 실패: productId={}", productId, e)
            }
        }
        return total
    }

    private fun expireLocked(productId: Long, reservationIds: List<String>): Int =
        transactionTemplate.execute {
            val locked = inventoryReservationRepository.lockReservedByReservationIds(reservationIds)
            if (locked.isEmpty()) return@execute 0
            inventoryReservationRepository.expireByIds(locked.map { (it[0] as Number).toLong() }, LocalDateTime.now())
            bucketStockService.restore(productId, locked.sumOf { (it[1] as Number).toInt() })
            inventoryCacheEvictor?.evictAfterCommit(productId)
            locked.size
        } ?: 0

    private fun expireClaimedLocked(productId: Long, reservationIds: List<String>): Int =
        transactionTemplate.execute {
            val locked = inventoryReservationRepository.lockClaimedByReservationIds(reservationIds)
            if (locked.isEmpty()) return@execute 0
            inventoryReservationRepository.expireClaimedByIds(locked.map { (it[0] as Number).toLong() }, LocalDateTime.now())
            bucketStockService.restore(productId, locked.sumOf { (it[1] as Number).toInt() })
            inventoryCacheEvictor?.evictAfterCommit(productId)
            locked.size
        } ?: 0

    data class CycleResult(val batches: Int, val linked: Int, val aborted: Int, val floor: Int, val failed: Boolean)
}
