package com.hoppingmall.mall.product.controller

import com.hoppingmall.mall.global.common.response.ApiResponse
import com.hoppingmall.mall.product.dto.request.ProductCreateRequest
import com.hoppingmall.mall.product.exception.ProductNotFoundException
import com.hoppingmall.mall.product.dto.request.ProductSearchCondition
import com.hoppingmall.mall.product.dto.request.ProductUpdateRequest
import com.hoppingmall.mall.product.dto.response.ProductImageResponse
import com.hoppingmall.mall.product.dto.response.ProductResponse
import com.hoppingmall.mall.product.service.ProductCommandService
import com.hoppingmall.mall.product.service.ProductImageService
import com.hoppingmall.mall.product.service.ProductQueryService
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/api/v1/products")
class ProductController(
    private val productQueryService: ProductQueryService,
    private val productCommandService: ProductCommandService,
    private val productImageService: ProductImageService
) {

    @GetMapping
    fun getProducts(pageable: Pageable): ApiResponse<Page<ProductResponse>> {
        val products = productQueryService.getProducts(pageable)
        return ApiResponse.success(products)
    }

    @GetMapping("/{productId}")
    fun getProduct(@PathVariable productId: Long): ApiResponse<ProductResponse> {
        val productResponse = productQueryService.getProductById(productId)
            ?: throw ProductNotFoundException()
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

    @GetMapping("/category/{categoryId}")
    fun getProductsByCategory(
        @PathVariable categoryId: Long,
        pageable: Pageable
    ): ApiResponse<Page<ProductResponse>> {
        val products = productQueryService.getProductsByCategoryId(categoryId, pageable)
        return ApiResponse.success(products)
    }

    @GetMapping("/search")
    fun searchProducts(
        @ModelAttribute condition: ProductSearchCondition,
        pageable: Pageable
    ): ApiResponse<Page<ProductResponse>> {
        val products = productQueryService.searchProducts(condition, pageable)
        return ApiResponse.success(products)
    }

    @PostMapping
    fun createProduct(@Valid @RequestBody request: ProductCreateRequest): ApiResponse<ProductResponse> {
        val productResponse = productCommandService.createProduct(request)
        return ApiResponse.success(productResponse)
    }

    @PutMapping("/{productId}")
    fun updateProduct(
        @PathVariable productId: Long,
        @Valid @RequestBody request: ProductUpdateRequest
    ): ApiResponse<ProductResponse> {
        val productResponse = productCommandService.updateProduct(productId, request)
        return ApiResponse.success(productResponse)
    }

    @DeleteMapping("/{productId}")
    fun deleteProduct(@PathVariable productId: Long): ApiResponse<Unit> {
        productCommandService.deleteProduct(productId)
        return ApiResponse.success(Unit)
    }

    @PostMapping("/images/upload")
    fun uploadProductImage(@RequestParam("image") imageFile: MultipartFile): ApiResponse<ProductImageResponse> {
        val productImageResponse = productImageService.uploadProductImage(imageFile)
        return ApiResponse.success(productImageResponse)
    }
} 