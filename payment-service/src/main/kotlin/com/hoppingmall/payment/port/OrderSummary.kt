package com.hoppingmall.payment.port

import com.fasterxml.jackson.annotation.JsonAlias
import java.math.BigDecimal

data class OrderSummary(
    @JsonAlias("orderId")
    val orderId: Long,
    @JsonAlias("buyerId")
    val buyerId: Long,
    @JsonAlias("totalAmount")
    val totalAmount: BigDecimal,
    val status: String
)
