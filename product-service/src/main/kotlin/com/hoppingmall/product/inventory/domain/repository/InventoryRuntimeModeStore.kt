package com.hoppingmall.product.inventory.domain.repository

import com.hoppingmall.product.inventory.domain.InventoryRuntimeMode
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.Timestamp

@Repository
class InventoryRuntimeModeStore(
    private val jdbcTemplate: JdbcTemplate
) {

    fun find(): InventoryRuntimeMode? =
        jdbcTemplate.query(
            FIND_SQL,
            { rs, _ ->
                InventoryRuntimeMode(
                    id = rs.getInt("id"),
                    mode = rs.getString("mode"),
                    bucketCount = (rs.getObject("bucket_count") as Number?)?.toInt(),
                    updatedAt = rs.getTimestamp("updated_at").toLocalDateTime()
                )
            },
            InventoryRuntimeMode.SINGLETON_ID
        ).firstOrNull()

    fun insert(mode: InventoryRuntimeMode) {
        jdbcTemplate.update(INSERT_SQL, mode.id, mode.mode, mode.bucketCount, Timestamp.valueOf(mode.updatedAt))
    }

    fun hasBuckets(): Boolean = jdbcTemplate.queryForList(ANY_BUCKET_SQL, Int::class.java).isNotEmpty()

    fun hasInventoryStock(): Boolean = jdbcTemplate.queryForList(ANY_STOCK_SQL, Int::class.java).isNotEmpty()

    companion object {
        private const val FIND_SQL =
            "SELECT id, mode, bucket_count, updated_at FROM inventory_runtime_mode WHERE id = ?"
        private const val INSERT_SQL =
            "INSERT INTO inventory_runtime_mode (id, mode, bucket_count, updated_at) VALUES (?, ?, ?, ?)"
        private const val ANY_BUCKET_SQL = "SELECT 1 FROM inventory_buckets LIMIT 1"
        private const val ANY_STOCK_SQL = "SELECT 1 FROM inventories WHERE stock_quantity > 0 AND deleted_at IS NULL LIMIT 1"
    }
}
