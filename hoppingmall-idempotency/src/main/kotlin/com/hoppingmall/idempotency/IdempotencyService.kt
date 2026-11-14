package com.hoppingmall.idempotency

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class IdempotencyService(
    private val idempotencyRecordRepository: IdempotencyRecordRepository
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Value("\${idempotency.cleanup.max-batches:1000}")
    private var cleanupMaxBatches: Int = 1000

    @Transactional(readOnly = true)
    fun findByKey(key: String): IdempotencyRecord? =
        idempotencyRecordRepository.findByIdempotencyKey(key)
            ?.takeIf { it.expiresAt.isAfter(LocalDateTime.now()) }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun save(
        key: String,
        httpMethod: String,
        endpoint: String,
        responseStatus: Int,
        responseBody: String,
        ttlHours: Long
    ): IdempotencyRecord =
        idempotencyRecordRepository.save(
            IdempotencyRecord(
                idempotencyKey = key,
                httpMethod = httpMethod,
                endpoint = endpoint,
                responseStatus = responseStatus,
                responseBody = responseBody,
                expiresAt = LocalDateTime.now().plusHours(ttlHours)
            )
        )

    @Scheduled(cron = "0 0 3 * * ?")
    fun cleanupExpiredRecords() {
        val now = LocalDateTime.now()
        var deletedCount = 0
        var batches = 0
        do {
            val deleted = idempotencyRecordRepository.deleteExpiredRecords(now)
            deletedCount += deleted
            batches++
        } while (deleted == IDEMPOTENCY_CLEANUP_BATCH_SIZE && batches < cleanupMaxBatches)
        if (deletedCount > 0) {
            log.info("멱등성 만료 레코드 정리: {}건 삭제 ({}회)", deletedCount, batches)
        }
    }
}
