package com.hoppingmall.product.common.file

import com.hoppingmall.product.common.file.storage.FileStorage
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import java.sql.Timestamp
import java.time.LocalDateTime

@Component
class OrphanUploadCleaner(
    private val jdbcTemplate: JdbcTemplate,
    private val fileStorage: FileStorage,
    private val properties: FileStorageProperties
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun cleanUp(now: LocalDateTime): Int {
        val cutoff = Timestamp.valueOf(now.minusHours(properties.orphanTtlHours))
        jdbcTemplate.update(MARK_DELETING, Timestamp.valueOf(now), cutoff, properties.orphanBatchSize)
        val targets = jdbcTemplate.query(
            SELECT_DELETING,
            { rs, _ -> rs.getLong("id") to rs.getString("object_key") },
            properties.orphanBatchSize
        )
        var deleted = 0
        targets.forEach { (id, key) ->
            try {
                fileStorage.delete(key)
                jdbcTemplate.update(DELETE_ROW, id)
                deleted++
            } catch (e: Exception) {
                log.warn("주인 없는 업로드 삭제 실패, 다음 주기에 다시 시도: key={}, error={}", key, e.toString())
            }
        }
        if (deleted > 0) {
            log.info("주인 없는 업로드 정리: deleted={}, ttlHours={}", deleted, properties.orphanTtlHours)
        }
        return deleted
    }

    companion object {
        private val MARK_DELETING = """
            UPDATE uploaded_files
            SET status = 'DELETING', updated_at = ?
            WHERE status = 'PENDING'
              AND deleted_at IS NULL
              AND created_at < ?
            ORDER BY id
            LIMIT ?
        """.trimIndent()

        private val SELECT_DELETING = """
            SELECT id, object_key FROM uploaded_files
            WHERE status = 'DELETING'
            ORDER BY id
            LIMIT ?
        """.trimIndent()

        private const val DELETE_ROW = "DELETE FROM uploaded_files WHERE id = ? AND status = 'DELETING'"
    }
}
