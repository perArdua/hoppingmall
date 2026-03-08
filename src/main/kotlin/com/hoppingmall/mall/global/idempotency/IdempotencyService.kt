package com.hoppingmall.mall.global.idempotency

import org.slf4j.LoggerFactory
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

    @Transactional(readOnly = true)
    fun findByKey(key: String): IdempotencyRecord? {
        return idempotencyRecordRepository.findByIdempotencyKey(key)
            .filter { it.expiresAt.isAfter(LocalDateTime.now()) }
            .orElse(null)
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun save(
        key: String,
        httpMethod: String,
        endpoint: String,
        responseStatus: Int,
        responseBody: String,
        ttlHours: Long
    ): IdempotencyRecord {
        return idempotencyRecordRepository.save(
            IdempotencyRecord(
                idempotencyKey = key,
                httpMethod = httpMethod,
                endpoint = endpoint,
                responseStatus = responseStatus,
                responseBody = responseBody,
                expiresAt = LocalDateTime.now().plusHours(ttlHours)
            )
        )
    }

    @Scheduled(cron = "0 0 3 * * ?")
    @Transactional
    fun cleanupExpiredRecords() {
        val deleted = idempotencyRecordRepository.deleteExpiredRecords(LocalDateTime.now())
        if (deleted > 0) {
            log.info("멱등성 만료 레코드 정리: {}건 삭제", deleted)
        }
    }
}
