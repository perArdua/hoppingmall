package com.hoppingmall.product.inventory.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "inventory_runtime_mode")
class InventoryRuntimeMode(
    @Id
    val id: Int,

    @Column(nullable = false, length = 16)
    val mode: String,

    val bucketCount: Int?,

    @Column(nullable = false)
    val updatedAt: LocalDateTime
) {
    companion object {
        const val SINGLETON_ID = 1
    }
}
