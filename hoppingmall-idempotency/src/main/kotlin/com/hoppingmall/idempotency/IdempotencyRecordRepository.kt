package com.hoppingmall.idempotency

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

const val IDEMPOTENCY_CLEANUP_BATCH_SIZE = 1000

interface IdempotencyRecordRepository : JpaRepository<IdempotencyRecord, Long> {
    fun findByIdempotencyKey(idempotencyKey: String): IdempotencyRecord?

    @Transactional
    @Modifying
    @Query(
        value = "DELETE FROM idempotency_records WHERE expires_at < :now LIMIT $IDEMPOTENCY_CLEANUP_BATCH_SIZE",
        nativeQuery = true
    )
    fun deleteExpiredRecords(@Param("now") now: LocalDateTime): Int
}
