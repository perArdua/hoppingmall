package com.hoppingmall.product.category.domain

import com.hoppingmall.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table
@Entity
@Table(
    name = "categories",
    indexes = [Index(name = "idx_categories_parent_id", columnList = "parentCategoryId")]
)
class Category private constructor(
    @Column(nullable = false, unique = true)
    var name: String,

    @Column
    val parentCategoryId: Long?,

    @Column(nullable = false)
    val depth: Int
) : BaseEntity() {

    fun update(name: String) {
        this.name = name
    }

    fun isRootCategory(): Boolean = parentCategoryId == null

    companion object {
        fun create(name: String, parentCategoryId: Long?, depth: Int): Category =
            Category(name = name, parentCategoryId = parentCategoryId, depth = depth)
    }
}
