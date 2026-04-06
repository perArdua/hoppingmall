package com.hoppingmall.order.config

import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.config.EnableJpaAuditing
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

@Configuration
@EnableJpaAuditing
@EntityScan(basePackages = ["com.hoppingmall.order", "com.hoppingmall.idempotency", "com.hoppingmall.dlq", "com.hoppingmall.outbox"])
@EnableJpaRepositories(basePackages = ["com.hoppingmall.order", "com.hoppingmall.idempotency", "com.hoppingmall.dlq", "com.hoppingmall.outbox"])
class JpaConfig
