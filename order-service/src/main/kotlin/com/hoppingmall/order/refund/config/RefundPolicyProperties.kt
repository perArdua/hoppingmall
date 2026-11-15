package com.hoppingmall.order.refund.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "order.refund")
data class RefundPolicyProperties(
    val changeOfMindWindowDays: Long = 7,
    val sellerFaultWindowMonths: Long = 3
)
