package com.hoppingmall.mall.product.dto

import java.math.BigDecimal

interface TopProductProjection {
    fun getProductId(): Long
    fun getTotalAmount(): BigDecimal
    fun getTotalQuantity(): Long
}
