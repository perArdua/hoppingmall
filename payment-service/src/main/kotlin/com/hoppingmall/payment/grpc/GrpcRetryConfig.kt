package com.hoppingmall.payment.grpc

import io.github.resilience4j.retry.Retry
import io.github.resilience4j.retry.RetryConfig
import io.github.resilience4j.retry.RetryRegistry
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class GrpcRetryConfig {

    @Bean
    fun grpcRetry(retryRegistry: RetryRegistry): Retry {
        val base = retryRegistry.getConfiguration(GRPC_RETRY).orElseGet { retryRegistry.defaultConfig }
        return retryRegistry.retry(
            GRPC_RETRY,
            RetryConfig.from<Any>(base)
                .retryExceptions()
                .retryOnException(TransientGrpcFailurePredicate())
                .build()
        )
    }

    companion object {
        const val GRPC_RETRY = "grpc"
    }
}
