package com.hoppingmall.order.support

import org.springframework.jdbc.core.JdbcTemplate
import java.util.UUID

private const val SEED_CHUNK = 1_000
private const val ROWS_PER_SECOND = 1_700

fun JdbcTemplate.seedFenceRows(count: Int, decision: String, orderStatuses: List<String>, hoursAgo: Long): List<String> {
    val rids = List(count) { UUID.randomUUID().toString() }
    val firstOrderId = queryForObject("SELECT COALESCE(MAX(id), 0) + 1 FROM orders", Long::class.java)!!
    rids.withIndex().chunked(SEED_CHUNK).forEach { chunk ->
        update(
            "INSERT INTO reservation_fence (reservation_id, decision, created_at) VALUES " +
                chunk.joinToString { (index, rid) ->
                    "('$rid', '$decision', TIMESTAMPADD(SECOND, ${index / ROWS_PER_SECOND - hoursAgo * 3600}, CURRENT_DATE()))"
                }
        )
        if (orderStatuses.isNotEmpty()) {
            update(
                "INSERT INTO orders (id, buyer_id, status, total_amount, cancel_retry_count, created_at, updated_at) VALUES " +
                    chunk.joinToString { (index, _) ->
                        "(${firstOrderId + index}, 1, '${orderStatuses[index % orderStatuses.size]}', 1000, 0, NOW(6), NOW(6))"
                    }
            )
            update(
                "INSERT INTO order_items (order_id, seller_id, product_id, product_name, product_price, quantity, total_price, " +
                    "reservation_id, withdrawal_restricted, created_at, updated_at) VALUES " +
                    chunk.joinToString { (index, rid) ->
                        "(${firstOrderId + index}, 5, 100, '상품', 1000, 1, 1000, '$rid', false, NOW(6), NOW(6))"
                    }
            )
        }
    }
    return rids
}
