package com.hoppingmall.mall.product.dto.request

import java.math.BigDecimal

data class BulkProductRow(
    val rowNumber: Int,
    val name: String,
    val description: String,
    val categoryId: Long,
    val price: BigDecimal,
    val stockQuantity: Int,
    val imageUrls: List<String>
)
