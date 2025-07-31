package com.hoppingmall.mall.product.dto.request

import com.hoppingmall.mall.global.enums.ProductStatus
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive

data class ProductUpdateRequest(
    @field:NotBlank(message = "상품명은 필수입니다.")
    val name: String,
    
    @field:NotBlank(message = "상품 설명은 필수입니다.")
    val description: String,
    
    @field:Positive(message = "가격은 0보다 커야 합니다.")
    val price: Long,
    
    val imageUrl: String? = null,
    
    val status: ProductStatus = ProductStatus.AVAILABLE
) 