package com.hoppingmall.payment.coupon.dto.event

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.hoppingmall.payment.coupon.enum.CouponRestoreReason
import java.time.LocalDateTime

data class CouponRestoreEvent @JsonCreator constructor(
    @JsonProperty("couponId") val couponId: Long,
    @JsonProperty("userId") val userId: Long,
    @JsonProperty("reason") val reason: CouponRestoreReason,
    @JsonProperty("occurredAt") val occurredAt: LocalDateTime = LocalDateTime.now(),
    @JsonProperty("retryCount") val retryCount: Int = 0
) {
    fun nextRetry(): CouponRestoreEvent = copy(retryCount = retryCount + 1)
}
