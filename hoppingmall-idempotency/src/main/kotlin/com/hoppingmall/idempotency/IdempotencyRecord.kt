package com.hoppingmall.idempotency

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "idempotency_records")
class IdempotencyRecord(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false, unique = true, length = 64)
    val idempotencyKey: String,

    @Column(nullable = false, length = 10)
    val httpMethod: String,

    @Column(nullable = false)
    val endpoint: String,

    @Column(nullable = false)
    val responseStatus: Int,

    @Column(nullable = false, columnDefinition = "TEXT")
    val responseBody: String,

    @Column(nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(nullable = false)
    val expiresAt: LocalDateTime
)
