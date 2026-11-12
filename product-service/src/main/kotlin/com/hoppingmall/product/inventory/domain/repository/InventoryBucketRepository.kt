package com.hoppingmall.product.inventory.domain.repository

import com.hoppingmall.product.inventory.domain.InventoryBucket
import com.hoppingmall.product.inventory.domain.InventoryBucketId
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface InventoryBucketRepository : JpaRepository<InventoryBucket, InventoryBucketId> {

    @Query("SELECT COALESCE(SUM(b.stockQuantity), 0) FROM InventoryBucket b WHERE b.productId = :productId")
    fun sumStock(@Param("productId") productId: Long): Long

    @Query("SELECT b.productId, SUM(b.stockQuantity) FROM InventoryBucket b WHERE b.productId IN :productIds GROUP BY b.productId")
    fun sumStockByProductIds(@Param("productIds") productIds: List<Long>): List<Array<Any>>

    fun countByProductId(productId: Long): Long
}
