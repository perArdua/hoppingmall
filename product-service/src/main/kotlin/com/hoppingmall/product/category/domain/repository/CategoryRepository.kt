package com.hoppingmall.product.category.domain.repository

import com.hoppingmall.product.category.domain.Category
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface CategoryRepository : JpaRepository<Category, Long> {
    fun existsByName(name: String): Boolean
    fun existsByNameAndIdNot(name: String, id: Long): Boolean
    fun findByParentCategoryId(parentCategoryId: Long): List<Category>
    fun existsByParentCategoryId(parentCategoryId: Long): Boolean
    fun findNullableById(id: Long): Category?
    fun findByParentCategoryIdIsNull(): List<Category>
}
