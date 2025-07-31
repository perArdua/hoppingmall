package com.hoppingmall.mall.product.controller

import com.hoppingmall.mall.global.common.response.ApiResponse
import com.hoppingmall.mall.product.dto.response.ProductResponse
import com.hoppingmall.mall.product.service.ProductQueryService
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/products")
class ProductController(
    private val productQueryService: ProductQueryService
) {

    @GetMapping
    fun getProducts(pageable: Pageable): ApiResponse<Page<ProductResponse>> {
        val products = productQueryService.getProducts(pageable)
        return ApiResponse.success(products)
    }

    @GetMapping("/{productId}")
    fun getProduct(@PathVariable productId: Long): ApiResponse<ProductResponse> {
        val productResponse = productQueryService.getProductById(productId)
        return ApiResponse.success(productResponse)
    }

    @GetMapping("/seller/{sellerId}")
    fun getProductsBySeller(
        @PathVariable sellerId: Long,
        pageable: Pageable
    ): ApiResponse<Page<ProductResponse>> {
        val products = productQueryService.getProductsBySellerId(sellerId, pageable)
        return ApiResponse.success(products)
    }
} 