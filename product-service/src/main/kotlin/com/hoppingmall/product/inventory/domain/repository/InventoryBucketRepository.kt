package com.hoppingmall.product.inventory.domain.repository

import com.hoppingmall.product.inventory.domain.InventoryBucket
import com.hoppingmall.product.inventory.domain.InventoryBucketId
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface InventoryBucketRepository : JpaRepository<InventoryBucket, InventoryBucketId> {

    @Query("SELECT COALESCE(SUM(b.stockQuantity), 0) FROM InventoryBucket b WHERE b.productId = :productId")
    fun sumStock(@Param("productId") productId: Long): Long

    fun countByProductId(productId: Long): Long
}
