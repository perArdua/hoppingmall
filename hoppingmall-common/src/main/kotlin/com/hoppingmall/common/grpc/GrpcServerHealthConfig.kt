package com.hoppingmall.common.grpc

import net.devh.boot.grpc.server.serverfactory.GrpcServerLifecycle
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(
    name = [
        "net.devh.boot.grpc.server.serverfactory.GrpcServerLifecycle",
        "org.springframework.boot.actuate.health.HealthIndicator"
    ]
)
class GrpcServerHealthConfig {

    @Bean
    fun grpcServerHealthIndicator(lifecycles: ObjectProvider<GrpcServerLifecycle>): HealthIndicator =
        GrpcServerHealthIndicator(lifecycles)
}
