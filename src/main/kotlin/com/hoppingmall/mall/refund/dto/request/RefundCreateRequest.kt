package com.hoppingmall.mall.refund.dto.request

import com.hoppingmall.mall.refund.enum.RefundReason
import jakarta.validation.Valid
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size

data class RefundCreateRequest(
    @field:NotNull(message = "주문 ID는 필수입니다.")
    @field:Positive(message = "주문 ID는 양수여야 합니다.")
    val orderId: Long,

    @field:NotNull(message = "환불 사유는 필수입니다.")
    val reason: RefundReason,

    val reasonDetail: String? = null,

    @field:NotNull(message = "환불 아이템 목록은 필수입니다.")
    @field:Size(min = 1, message = "환불 아이템은 최소 1개 이상이어야 합니다.")
    @field:Valid
    val items: List<RefundItemRequest>
)

data class RefundItemRequest(
    @field:NotNull(message = "주문 아이템 ID는 필수입니다.")
    @field:Positive(message = "주문 아이템 ID는 양수여야 합니다.")
    val orderItemId: Long,

    @field:NotNull(message = "환불 수량은 필수입니다.")
    @field:Positive(message = "환불 수량은 양수여야 합니다.")
    val quantity: Int
)
