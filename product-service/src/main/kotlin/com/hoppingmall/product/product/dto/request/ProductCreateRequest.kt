package com.hoppingmall.product.product.dto.request

import com.fasterxml.jackson.annotation.JsonIgnore
import com.hoppingmall.product.common.enums.ProductStatus
import com.hoppingmall.product.common.enums.WithdrawalRestrictionReason
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import java.math.BigDecimal

data class ProductCreateRequest(
    val sellerId: Long? = null,

    @field:NotNull(message = "카테고리 ID는 필수입니다.")
    val categoryId: Long?,

    @field:NotBlank(message = "상품명은 필수입니다.")
    val name: String,
    
    @field:NotBlank(message = "상품 설명은 필수입니다.")
    val description: String,
    
    @field:Positive(message = "가격은 0보다 커야 합니다.")
    val price: BigDecimal,
    
    val imageUrls: List<String>? = null,
    
    @field:NotNull(message = "상품 상태는 필수입니다.")
    val status: ProductStatus = ProductStatus.AVAILABLE,

    @field:Min(value = 0, message = "재고 수량은 0 이상이어야 합니다.")
    val stockQuantity: Int? = null,

    val withdrawalRestricted: Boolean = false,

    val withdrawalRestrictionReason: WithdrawalRestrictionReason? = null
) {
    @get:JsonIgnore
    @get:AssertTrue(message = "청약철회를 제한하면 사유를 정해야 하고, 제한하지 않으면 사유를 둘 수 없습니다.")
    val isWithdrawalRestrictionConsistent: Boolean
        get() = withdrawalRestricted == (withdrawalRestrictionReason != null)
} 