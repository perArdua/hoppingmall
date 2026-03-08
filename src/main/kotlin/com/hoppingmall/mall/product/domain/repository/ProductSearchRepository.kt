package com.hoppingmall.mall.product.domain.repository

import com.hoppingmall.mall.global.enums.ProductStatus
import com.hoppingmall.mall.product.domain.Product
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
