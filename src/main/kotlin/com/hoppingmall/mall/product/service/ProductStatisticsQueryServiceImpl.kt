package com.hoppingmall.mall.product.service

import com.hoppingmall.mall.product.domain.repository.ProductStatisticsRepository
import com.hoppingmall.mall.product.dto.response.ProductStatisticsResponse
import com.hoppingmall.mall.product.dto.response.ProductStatisticsSummaryResponse
import com.hoppingmall.mall.product.exception.ProductStatisticsNotFoundException
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class ProductStatisticsQueryServiceImpl(
    private val productStatisticsRepository: ProductStatisticsRepository
) : ProductStatisticsQueryService {

    override fun getAll(pageable: Pageable): Page<ProductStatisticsResponse> {
        return productStatisticsRepository.findAll(pageable)
            .map { ProductStatisticsResponse.from(it) }
    }

    override fun getByProductId(productId: Long): ProductStatisticsResponse {
        val statistics = productStatisticsRepository.findByProductId(productId)
            ?: throw ProductStatisticsNotFoundException()
        return ProductStatisticsResponse.from(statistics)
    }

    override fun getBySellerId(sellerId: Long, pageable: Pageable): Page<ProductStatisticsResponse> {
        return productStatisticsRepository.findBySellerId(sellerId, pageable)
            .map { ProductStatisticsResponse.from(it) }
    }

    override fun getByCategoryId(categoryId: Long, pageable: Pageable): Page<ProductStatisticsResponse> {
        return productStatisticsRepository.findByCategoryId(categoryId, pageable)
            .map { ProductStatisticsResponse.from(it) }
    }

    override fun getSummary(): ProductStatisticsSummaryResponse {
        return ProductStatisticsSummaryResponse(
            totalProductCount = productStatisticsRepository.countAllProducts(),
            totalSalesAmount = productStatisticsRepository.sumTotalSalesAmount(),
            totalRefundAmount = productStatisticsRepository.sumTotalRefundAmount(),
            averageRefundRate = productStatisticsRepository.avgRefundRate()
        )
    }
}
