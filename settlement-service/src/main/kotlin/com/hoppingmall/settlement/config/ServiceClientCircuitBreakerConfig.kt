package com.hoppingmall.settlement.config

import io.github.resilience4j.common.circuitbreaker.configuration.CircuitBreakerConfigCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.HttpClientErrorException

@Configuration
class ServiceClientCircuitBreakerConfig {

    @Bean
    fun sellerQueryClientErrorCustomizer(): CircuitBreakerConfigCustomizer = ignoreClientErrors("seller-query")

    @Bean
    fun orderItemQueryClientErrorCustomizer(): CircuitBreakerConfigCustomizer = ignoreClientErrors("order-item-query")

    @Bean
    fun refundQueryClientErrorCustomizer(): CircuitBreakerConfigCustomizer = ignoreClientErrors("refund-query")

    private fun ignoreClientErrors(name: String): CircuitBreakerConfigCustomizer =
        CircuitBreakerConfigCustomizer.of(name) { builder ->
            builder.ignoreException { it is HttpClientErrorException }
        }
}
