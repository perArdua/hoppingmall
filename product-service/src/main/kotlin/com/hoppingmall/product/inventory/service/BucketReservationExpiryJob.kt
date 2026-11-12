package com.hoppingmall.product.inventory.service

import com.hoppingmall.product.inventory.domain.repository.InventoryReservationRepository
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.DistributionSummary
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Profile
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDateTime

@Service
@Profile("!test")
@ConditionalOnProperty(name = ["inventory.mode"], havingValue = "bucket")
class BucketReservationExpiryJob(
    private val inventoryReservationRepository: InventoryReservationRepository,
    private val bucketStockService: BucketStockService,
    transactionManager: PlatformTransactionManager,
    meterRegistry: MeterRegistry,
    @Value("\${inventory.expiry.enabled:true}") private val expiryEnabled: Boolean,
    @Value("\${inventory.expiry.chunk-size:1000}") private val chunkSize: Int
) {
    private val logger = LoggerFactory.getLogger(BucketReservationExpiryJob::class.java)

    private val transactionTemplate = TransactionTemplate(transactionManager)

    private val batchSize = DistributionSummary.builder("inventory.expiry.batch.size")
        .description("Reservations expired per chunk transaction")
        .register(meterRegistry)

    private val chunkTimer = Timer.builder("inventory.expiry.tx")
        .description("Duration of one expiry chunk transaction")
        .register(meterRegistry)

    private val casMismatchCounter = Counter.builder("inventory.expiry.cas_mismatch")
        .description("Expiry chunks rolled back because some reservations changed status after they were read")
        .register(meterRegistry)

    @Scheduled(fixedDelayString = "\${inventory.reservation.expiry-interval-ms:60000}")
    fun expireReservations() {
        if (!expiryEnabled) return
        val now = LocalDateTime.now()
        val backlog = inventoryReservationRepository.countExpiredGroupByProduct(now)
            .map { row -> (row[0] as Number).toLong() to (row[1] as Number).toLong() }
            .sortedBy { it.first }
        if (backlog.isEmpty()) return

        logger.info("만료 예약 처리 시작: 상품 {}개, {}건", backlog.size, backlog.sumOf { it.second })
        backlog.forEach { (productId, _) ->
            try {
                drainProduct(productId, now)
            } catch (e: Exception) {
                logger.error("만료 예약 처리 실패: productId={}", productId, e)
            }
        }
    }

    fun drainProduct(productId: Long, now: LocalDateTime) {
        var mismatches = 0
        while (mismatches < MAX_CAS_MISMATCHES) {
            val expired = chunkTimer.recordCallable {
                transactionTemplate.execute { status ->
                    expireChunk(productId, now).also { if (it == CAS_MISMATCH) status.setRollbackOnly() }
                }
            } ?: 0
            if (expired == CAS_MISMATCH) {
                casMismatchCounter.increment()
                mismatches++
                continue
            }
            if (expired < chunkSize) return
            mismatches = 0
        }
        logger.warn("만료 예약 CAS 불일치 반복으로 이번 주기 중단: productId={}", productId)
    }

    private fun expireChunk(productId: Long, now: LocalDateTime): Int {
        val rows = inventoryReservationRepository.findExpiredChunk(productId, now, chunkSize)
        if (rows.isEmpty()) return 0

        val ids = rows.map { (it[0] as Number).toLong() }
        val quantity = rows.sumOf { (it[1] as Number).toInt() }
        val updated = inventoryReservationRepository.expireByIds(ids, LocalDateTime.now())
        if (updated != ids.size) return CAS_MISMATCH

        bucketStockService.restore(productId, quantity)
        batchSize.record(ids.size.toDouble())
        return ids.size
    }

    companion object {
        private const val CAS_MISMATCH = -1
        private const val MAX_CAS_MISMATCHES = 3
    }
}
