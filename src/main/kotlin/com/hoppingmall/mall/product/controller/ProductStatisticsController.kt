package com.hoppingmall.mall.product.controller

import com.hoppingmall.mall.global.common.response.ApiResponse
import com.hoppingmall.mall.product.dto.response.ProductStatisticsResponse
import com.hoppingmall.mall.product.dto.response.ProductStatisticsSummaryResponse
import com.hoppingmall.mall.product.service.ProductStatisticsQueryService
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/admin/product-statistics")
class ProductStatisticsController(
    private val productStatisticsQueryService: ProductStatisticsQueryService
) {
    @GetMapping
    fun getAll(pageable: Pageable): ApiResponse<Page<ProductStatisticsResponse>> {
        return ApiResponse.success(productStatisticsQueryService.getAll(pageable))
    }

    @GetMapping("/{productId}")
    fun getByProductId(@PathVariable productId: Long): ApiResponse<ProductStatisticsResponse> {
        return ApiResponse.success(productStatisticsQueryService.getByProductId(productId))
    }

    @GetMapping("/seller/{sellerId}")
    fun getBySellerId(
        @PathVariable sellerId: Long,
        pageable: Pageable
    ): ApiResponse<Page<ProductStatisticsResponse>> {
        return ApiResponse.success(productStatisticsQueryService.getBySellerId(sellerId, pageable))
    }

    @GetMapping("/category/{categoryId}")
    fun getByCategoryId(
        @PathVariable categoryId: Long,
        pageable: Pageable
    ): ApiResponse<Page<ProductStatisticsResponse>> {
        return ApiResponse.success(productStatisticsQueryService.getByCategoryId(categoryId, pageable))
    }

    @GetMapping("/summary")
    fun getSummary(): ApiResponse<ProductStatisticsSummaryResponse> {
        return ApiResponse.success(productStatisticsQueryService.getSummary())
    }
}
