package com.hoppingmall.product.inventory.domain

import com.hoppingmall.common.BaseEntity
import com.hoppingmall.product.inventory.exception.InventoryInsufficientStockException
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import org.hibernate.annotations.Filter

@Filter(name = "softDeleteFilter", condition = "deleted_at IS NULL")
@Entity
@Table(name = "inventories")
class Inventory private constructor(
    @Column(unique = true, nullable = false)
    val productId: Long,

    @Column(nullable = false)
    var stockQuantity: Int
) : BaseEntity() {

    fun decreaseStock(quantity: Int) {
        if (!hasStock(quantity)) {
            throw InventoryInsufficientStockException()
        }
        stockQuantity -= quantity
    }

    fun increaseStock(quantity: Int) {
        stockQuantity += quantity
    }

    fun hasStock(quantity: Int): Boolean {
        return stockQuantity >= quantity
    }

    companion object {
        fun create(productId: Long, stockQuantity: Int): Inventory {
            return Inventory(productId = productId, stockQuantity = stockQuantity)
        }
    }
}
