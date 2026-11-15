package com.hoppingmall.product.product.service

import com.hoppingmall.product.product.dto.response.ProductResponse
import org.springframework.stereotype.Component

@Component
class ProductStockReader {

    fun withInStock(response: ProductResponse): ProductResponse = response
}
