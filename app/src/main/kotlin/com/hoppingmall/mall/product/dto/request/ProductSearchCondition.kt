package com.hoppingmall.mall.product.dto.request

import com.hoppingmall.mall.global.enums.ProductStatus
import java.math.BigDecimal

data class ProductSearchCondition(
    val keyword: String? = null,
    val categoryId: Long? = null,
    val status: ProductStatus? = null,
    val minPrice: BigDecimal? = null,
    val maxPrice: BigDecimal? = null
)
