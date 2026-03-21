package com.hoppingmall.product.product.domain.repository

import com.hoppingmall.product.common.enums.ProductStatus
import com.hoppingmall.product.product.domain.Product
import jakarta.persistence.EntityManager
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.domain.SliceImpl
import java.math.BigDecimal

class ProductSearchRepositoryImpl(
    private val entityManager: EntityManager,
    @Value("\${spring.datasource.url:}")
    private val datasourceUrl: String
) : ProductSearchRepository {

    private val useFullText: Boolean = datasourceUrl.contains("mysql", ignoreCase = true)

    override fun searchProducts(
        keyword: String?,
        categoryId: Long?,
        status: ProductStatus?,
        minPrice: BigDecimal?,
        maxPrice: BigDecimal?,
        pageable: Pageable
    ): Slice<Product> {
        return if (useFullText && !keyword.isNullOrBlank()) {
            searchWithFullText(keyword, categoryId, status, minPrice, maxPrice, pageable)
        } else {
            searchWithLike(keyword, categoryId, status, minPrice, maxPrice, pageable)
        }
    }

    private fun searchWithFullText(
        keyword: String,
        categoryId: Long?,
        status: ProductStatus?,
        minPrice: BigDecimal?,
        maxPrice: BigDecimal?,
        pageable: Pageable
    ): Slice<Product> {
        val sb = StringBuilder("SELECT p.* FROM products p WHERE ")
        sb.append("MATCH(p.name, p.description) AGAINST(:keyword IN BOOLEAN MODE)")
        sb.append(" AND p.deleted_at IS NULL")

        appendCommonConditions(sb, categoryId, status, minPrice, maxPrice)
        sb.append(" ORDER BY MATCH(p.name, p.description) AGAINST(:keyword IN BOOLEAN MODE) DESC")
        sb.append(" LIMIT :limit OFFSET :offset")

        val query = entityManager.createNativeQuery(sb.toString(), Product::class.java)
        query.setParameter("keyword", keyword)
        setCommonNativeParameters(query, categoryId, status, minPrice, maxPrice)
        query.setParameter("limit", pageable.pageSize + 1)
        query.setParameter("offset", pageable.offset)

        @Suppress("UNCHECKED_CAST")
        val results = query.resultList as List<Product>
        return toSlice(results, pageable)
    }

    private fun searchWithLike(
        keyword: String?,
        categoryId: Long?,
        status: ProductStatus?,
        minPrice: BigDecimal?,
        maxPrice: BigDecimal?,
        pageable: Pageable
    ): Slice<Product> {
        val sb = StringBuilder("SELECT p FROM Product p WHERE p.deletedAt IS NULL")

        if (!keyword.isNullOrBlank()) {
            sb.append(" AND (p.name LIKE :keyword OR p.description LIKE :keyword)")
        }
        appendCommonJpqlConditions(sb, categoryId, status, minPrice, maxPrice)

        val query = entityManager.createQuery(sb.toString(), Product::class.java)
        if (!keyword.isNullOrBlank()) {
            query.setParameter("keyword", "%$keyword%")
        }
        setCommonJpqlParameters(query, categoryId, status, minPrice, maxPrice)
        query.firstResult = pageable.offset.toInt()
        query.maxResults = pageable.pageSize + 1

        val results = query.resultList
        return toSlice(results, pageable)
    }

    private fun appendCommonConditions(
        sb: StringBuilder,
        categoryId: Long?,
        status: ProductStatus?,
        minPrice: BigDecimal?,
        maxPrice: BigDecimal?
    ) {
        if (categoryId != null) sb.append(" AND p.category_id = :categoryId")
        if (status != null) sb.append(" AND p.status = :status")
        if (minPrice != null) sb.append(" AND p.price >= :minPrice")
        if (maxPrice != null) sb.append(" AND p.price <= :maxPrice")
    }

    private fun appendCommonJpqlConditions(
        sb: StringBuilder,
        categoryId: Long?,
        status: ProductStatus?,
        minPrice: BigDecimal?,
        maxPrice: BigDecimal?
    ) {
        if (categoryId != null) sb.append(" AND p.categoryId = :categoryId")
        if (status != null) sb.append(" AND p.status = :status")
        if (minPrice != null) sb.append(" AND p.price >= :minPrice")
        if (maxPrice != null) sb.append(" AND p.price <= :maxPrice")
    }

    private fun setCommonNativeParameters(
        query: jakarta.persistence.Query,
        categoryId: Long?,
        status: ProductStatus?,
        minPrice: BigDecimal?,
        maxPrice: BigDecimal?
    ) {
        if (categoryId != null) query.setParameter("categoryId", categoryId)
        if (status != null) query.setParameter("status", status.name)
        if (minPrice != null) query.setParameter("minPrice", minPrice)
        if (maxPrice != null) query.setParameter("maxPrice", maxPrice)
    }

    private fun setCommonJpqlParameters(
        query: jakarta.persistence.Query,
        categoryId: Long?,
        status: ProductStatus?,
        minPrice: BigDecimal?,
        maxPrice: BigDecimal?
    ) {
        if (categoryId != null) query.setParameter("categoryId", categoryId)
        if (status != null) query.setParameter("status", status)
        if (minPrice != null) query.setParameter("minPrice", minPrice)
        if (maxPrice != null) query.setParameter("maxPrice", maxPrice)
    }

    private fun toSlice(results: List<Product>, pageable: Pageable): Slice<Product> {
        val hasNext = results.size > pageable.pageSize
        val content = if (hasNext) results.subList(0, pageable.pageSize) else results
        return SliceImpl(content, pageable, hasNext)
    }
}
