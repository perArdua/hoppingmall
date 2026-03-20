package com.hoppingmall.product.inventory.domain.repository

import com.hoppingmall.product.inventory.domain.Inventory
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface InventoryRepository : JpaRepository<Inventory, Long> {
    fun findByProductId(productId: Long): Inventory?

    @Query("SELECT i FROM Inventory i WHERE i.productId = :productId")
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findByProductIdForUpdate(@Param("productId") productId: Long): Inventory?

    fun existsByProductId(productId: Long): Boolean

    fun findAllByProductIdIn(productIds: List<Long>): List<Inventory>
}
