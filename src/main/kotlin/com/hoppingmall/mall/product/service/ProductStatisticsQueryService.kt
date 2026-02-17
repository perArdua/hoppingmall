package com.hoppingmall.mall.product.service

import com.hoppingmall.mall.product.dto.response.ProductStatisticsResponse
import com.hoppingmall.mall.product.dto.response.ProductStatisticsSummaryResponse
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

interface ProductStatisticsQueryService {
    fun getAll(pageable: Pageable): Page<ProductStatisticsResponse>
    fun getByProductId(productId: Long): ProductStatisticsResponse
    fun getBySellerId(sellerId: Long, pageable: Pageable): Page<ProductStatisticsResponse>
    fun getByCategoryId(categoryId: Long, pageable: Pageable): Page<ProductStatisticsResponse>
    fun getSummary(): ProductStatisticsSummaryResponse
}
