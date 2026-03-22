package com.hoppingmall.payment.grpc

import io.grpc.ClientInterceptor
import io.grpc.ServerInterceptor
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.instrumentation.grpc.v1_6.GrpcTelemetry
import net.devh.boot.grpc.client.interceptor.GrpcGlobalClientInterceptor
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class GrpcTelemetryConfig(private val openTelemetry: OpenTelemetry) {

    private val grpcTelemetry = GrpcTelemetry.create(openTelemetry)

    @Bean
    @GrpcGlobalServerInterceptor
    fun otelGrpcServerInterceptor(): ServerInterceptor = grpcTelemetry.newServerInterceptor()

    @Bean
    @GrpcGlobalClientInterceptor
    fun otelGrpcClientInterceptor(): ClientInterceptor = grpcTelemetry.newClientInterceptor()
}
