package com.hoppingmall.idempotency

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.LocalDateTime

interface IdempotencyRecordRepository : JpaRepository<IdempotencyRecord, Long> {
    fun findByIdempotencyKey(idempotencyKey: String): IdempotencyRecord?

    @Modifying
    @Query("DELETE FROM IdempotencyRecord r WHERE r.expiresAt < :now")
    fun deleteExpiredRecords(now: LocalDateTime): Int
}
