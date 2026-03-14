package com.hoppingmall.payment.point.dto.request

import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal

data class PointPolicyRequest(
    @field:NotBlank(message = "정책 이름을 입력해주세요")
    val policyName: String,

    @field:NotNull(message = "적립률을 입력해주세요")
    @field:DecimalMin(value = "0.0", message = "적립률은 0 이상이어야 합니다")
    @field:DecimalMax(value = "1.0", message = "적립률은 1 이하여야 합니다")
    val earnRate: BigDecimal,

    @field:NotNull(message = "최대 적립률을 입력해주세요")
    @field:DecimalMin(value = "0.0", message = "최대 적립률은 0 이상이어야 합니다")
    @field:DecimalMax(value = "1.0", message = "최대 적립률은 1 이하여야 합니다")
    val maxEarnRate: BigDecimal,

    @field:NotNull(message = "최소 사용 금액을 입력해주세요")
    @field:DecimalMin(value = "0.01", message = "최소 사용 금액은 0.01 이상이어야 합니다")
    val minUseAmount: BigDecimal,

    @field:NotNull(message = "최대 사용 금액을 입력해주세요")
    @field:DecimalMin(value = "0.01", message = "최대 사용 금액은 0.01 이상이어야 합니다")
    val maxUseAmount: BigDecimal,

    val description: String? = null
)
