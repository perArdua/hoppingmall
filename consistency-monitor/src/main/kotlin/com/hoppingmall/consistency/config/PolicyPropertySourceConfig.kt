package com.hoppingmall.consistency.config

import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.PropertySource

@Configuration
@PropertySource("classpath:invariants.properties")
class PolicyPropertySourceConfig
