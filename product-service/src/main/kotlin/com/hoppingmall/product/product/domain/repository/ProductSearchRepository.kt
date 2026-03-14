package com.hoppingmall.product.product.domain.repository

import com.hoppingmall.product.common.enums.ProductStatus
import com.hoppingmall.product.product.domain.Product
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import java.math.BigDecimal

interface ProductSearchRepository {
    fun searchProducts(
        keyword: String?,
        categoryId: Long?,
        status: ProductStatus?,
        minPrice: BigDecimal?,
        maxPrice: BigDecimal?,
        pageable: Pageable
    ): Slice<Product>
}
