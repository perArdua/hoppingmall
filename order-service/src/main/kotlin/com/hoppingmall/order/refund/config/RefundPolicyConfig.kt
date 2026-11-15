package com.hoppingmall.order.refund.config

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(RefundPolicyProperties::class)
class RefundPolicyConfig
