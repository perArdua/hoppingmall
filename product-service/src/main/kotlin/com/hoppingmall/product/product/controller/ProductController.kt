package com.hoppingmall.product.product.controller

import com.hoppingmall.common.ApiResponse
import com.hoppingmall.product.product.dto.request.ProductCreateRequest
import com.hoppingmall.product.product.exception.ProductNotFoundException
import com.hoppingmall.product.product.dto.request.ProductSearchCondition
import com.hoppingmall.product.product.dto.request.ProductUpdateRequest
import com.hoppingmall.product.product.dto.response.ProductImageResponse
import com.hoppingmall.product.product.dto.response.ProductResponse
import com.hoppingmall.product.product.service.ProductCommandService
import com.hoppingmall.product.product.service.ProductImageService
import com.hoppingmall.product.product.service.ProductQueryService
import jakarta.validation.Valid
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/api/v1/products")
@Tag(name = "상품")
class ProductController(
    private val productQueryService: ProductQueryService,
    private val productCommandService: ProductCommandService,
    private val productImageService: ProductImageService
) {

    @GetMapping
    fun getProducts(pageable: Pageable): ApiResponse<Slice<ProductResponse>> {
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
    ): ApiResponse<Slice<ProductResponse>> {
        val products = productQueryService.getProductsBySellerId(sellerId, pageable)
        return ApiResponse.success(products)
    }

    @GetMapping("/category/{categoryId}")
    fun getProductsByCategory(
        @PathVariable categoryId: Long,
        pageable: Pageable
    ): ApiResponse<Slice<ProductResponse>> {
        val products = productQueryService.getProductsByCategoryId(categoryId, pageable)
        return ApiResponse.success(products)
    }

    @GetMapping("/search")
    fun searchProducts(
        @ModelAttribute condition: ProductSearchCondition,
        pageable: Pageable
    ): ApiResponse<Slice<ProductResponse>> {
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