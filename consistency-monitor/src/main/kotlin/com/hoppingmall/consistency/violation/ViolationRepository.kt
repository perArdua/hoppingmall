package com.hoppingmall.consistency.violation

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class ViolationRepository(private val jdbcTemplate: JdbcTemplate) {

    private val rowMapper = RowMapper { rs, _ ->
        Violation(
            id = rs.getLong("id"),
            invId = rs.getString("inv_id"),
            entityKey = rs.getString("entity_key"),
            severity = rs.getString("severity"),
            firstSeen = rs.getObject("first_seen", LocalDateTime::class.java),
            lastSeen = rs.getObject("last_seen", LocalDateTime::class.java),
            confirmedAt = rs.getObject("confirmed_at", LocalDateTime::class.java),
            resolvedAt = rs.getObject("resolved_at", LocalDateTime::class.java),
            ackedBy = rs.getString("acked_by"),
            resolutionNote = rs.getString("resolution_note"),
            snapshotJson = rs.getString("snapshot_json")
        )
    }

    fun now(): LocalDateTime =
        jdbcTemplate.queryForObject("SELECT CURRENT_TIMESTAMP(6)", LocalDateTime::class.java)!!

    fun findOpen(invId: String): List<Violation> =
        jdbcTemplate.query(
            "SELECT * FROM invariant_violations WHERE inv_id = ? AND resolved_at IS NULL",
            rowMapper,
            invId
        )

    fun findAll(openOnly: Boolean): List<Violation> =
        jdbcTemplate.query(
            "SELECT * FROM invariant_violations" +
                (if (openOnly) " WHERE resolved_at IS NULL" else "") +
                " ORDER BY id DESC",
            rowMapper
        )

    fun findById(id: Long): Violation? =
        jdbcTemplate.query("SELECT * FROM invariant_violations WHERE id = ?", rowMapper, id).firstOrNull()

    fun insert(
        invId: String,
        entityKey: String,
        severity: String,
        seenAt: LocalDateTime,
        confirmedAt: LocalDateTime?,
        snapshotJson: String
    ) {
        jdbcTemplate.update(
            "INSERT INTO invariant_violations " +
                "(inv_id, entity_key, severity, first_seen, last_seen, confirmed_at, snapshot_json) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)",
            invId, entityKey, severity, seenAt, seenAt, confirmedAt, snapshotJson
        )
    }

    fun touch(id: Long, seenAt: LocalDateTime, confirmedAt: LocalDateTime?, snapshotJson: String) {
        jdbcTemplate.update(
            "UPDATE invariant_violations SET last_seen = ?, confirmed_at = COALESCE(confirmed_at, ?), " +
                "snapshot_json = ? WHERE id = ?",
            seenAt, confirmedAt, snapshotJson, id
        )
    }

    fun resolve(id: Long, resolvedAt: LocalDateTime, ackedBy: String, resolutionNote: String): Boolean =
        jdbcTemplate.update(
            "UPDATE invariant_violations SET resolved_at = ?, acked_by = ?, resolution_note = ? " +
                "WHERE id = ? AND resolved_at IS NULL",
            resolvedAt, ackedBy, resolutionNote, id
        ) == 1
}
