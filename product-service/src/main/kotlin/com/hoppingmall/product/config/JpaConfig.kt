package com.hoppingmall.product.config

import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.config.EnableJpaAuditing
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

@Configuration
@EnableJpaAuditing
@EntityScan(basePackages = ["com.hoppingmall.product"])
@EnableJpaRepositories(basePackages = ["com.hoppingmall.product"])
class JpaConfig
