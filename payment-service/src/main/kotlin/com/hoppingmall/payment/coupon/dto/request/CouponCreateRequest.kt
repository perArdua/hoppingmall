package com.hoppingmall.payment.coupon.dto.request

import com.hoppingmall.payment.coupon.enum.DiscountType
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import java.math.BigDecimal
import java.time.LocalDateTime

data class CouponCreateRequest(
    @field:NotBlank(message = "쿠폰명은 필수입니다.")
    val name: String,

    @field:NotBlank(message = "쿠폰 코드는 필수입니다.")
    val code: String,

    @field:NotNull(message = "할인 타입은 필수입니다.")
    val discountType: DiscountType,

    @field:NotNull(message = "할인 값은 필수입니다.")
    @field:DecimalMin(value = "1", message = "할인 값은 1 이상이어야 합니다.")
    val discountValue: BigDecimal,

    @field:NotNull(message = "최소 주문 금액은 필수입니다.")
    @field:DecimalMin(value = "0", message = "최소 주문 금액은 0 이상이어야 합니다.")
    val minOrderAmount: BigDecimal,

    val maxDiscountAmount: BigDecimal? = null,

    @field:NotNull(message = "총 발급 수량은 필수입니다.")
    @field:Positive(message = "총 발급 수량은 양수여야 합니다.")
    val totalQuantity: Int,

    @field:NotNull(message = "유효 시작일은 필수입니다.")
    val validFrom: LocalDateTime,

    @field:NotNull(message = "유효 종료일은 필수입니다.")
    val validTo: LocalDateTime
)
