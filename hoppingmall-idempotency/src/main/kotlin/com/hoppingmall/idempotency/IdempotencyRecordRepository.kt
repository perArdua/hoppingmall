package com.hoppingmall.idempotency

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.LocalDateTime
import java.util.Optional

interface IdempotencyRecordRepository : JpaRepository<IdempotencyRecord, Long> {
    fun findByIdempotencyKey(idempotencyKey: String): Optional<IdempotencyRecord>

    @Modifying
    @Query("DELETE FROM IdempotencyRecord r WHERE r.expiresAt < :now")
    fun deleteExpiredRecords(now: LocalDateTime): Int
}
