package com.hoppingmall.consistency.violation

import java.time.LocalDateTime

data class Violation(
    val id: Long,
    val invId: String,
    val entityKey: String,
    val severity: String,
    val firstSeen: LocalDateTime,
    val lastSeen: LocalDateTime,
    val confirmedAt: LocalDateTime?,
    val resolvedAt: LocalDateTime?,
    val ackedBy: String?,
    val resolutionNote: String?,
    val snapshotJson: String
)
