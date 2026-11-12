package com.hoppingmall.product.inventory.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.Table

@Entity
@Table(name = "inventory_buckets")
@IdClass(InventoryBucketId::class)
class InventoryBucket(
    @Id
    @Column(nullable = false)
    val productId: Long,

    @Id
    @Column(nullable = false)
    val bucketNo: Int,

    @Column(nullable = false)
    var stockQuantity: Int
) {
    companion object {
        fun split(productId: Long, total: Int, count: Int): List<InventoryBucket> =
            (0 until count).map { bucketNo -> InventoryBucket(productId, bucketNo, shareOf(total, count, bucketNo)) }

        fun shareOf(total: Int, count: Int, bucketNo: Int): Int =
            total / count + if (bucketNo == 0) total % count else 0
    }
}
