package com.hoppingmall.product.category.domain.repository

import com.hoppingmall.product.category.domain.Category
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface CategoryRepository : JpaRepository<Category, Long> {
    fun existsByName(name: String): Boolean
    fun existsByNameAndIdNot(name: String, id: Long): Boolean
    fun findByParentCategoryId(parentCategoryId: Long): List<Category>
    fun existsByParentCategoryId(parentCategoryId: Long): Boolean
    fun findNullableById(id: Long): Category?
    fun findByParentCategoryIdIsNull(): List<Category>

    @Query("SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END FROM Product p WHERE p.categoryId = :categoryId")
    fun existsProductByCategoryId(@Param("categoryId") categoryId: Long): Boolean

    @Modifying
    @Query(value = "DELETE FROM categories WHERE name = :name AND deleted_at IS NOT NULL", nativeQuery = true)
    fun deleteSoftDeletedByName(@Param("name") name: String): Int
}
