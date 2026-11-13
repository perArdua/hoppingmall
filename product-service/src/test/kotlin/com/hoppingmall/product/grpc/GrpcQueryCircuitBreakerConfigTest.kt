package com.hoppingmall.product.grpc

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean
import org.springframework.core.io.ClassPathResource
import java.util.Properties

@DisplayName("gRPC 조회 서킷 브레이커 열림 대기 설정 검증")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class GrpcQueryCircuitBreakerConfigTest {

    private val properties: Properties = YamlPropertiesFactoryBean()
        .apply { setResources(ClassPathResource("application.yml")) }
        .getObject()!!

    @Test
    fun gRPC_조회_서킷은_기본_설정을_쓰되_열림_대기를_10초로_줄인다() {
        GRPC_QUERY_INSTANCES.forEach { name ->
            assertThat(properties.getProperty("$INSTANCES.$name.baseConfig")).isEqualTo("default")
            assertThat(properties.getProperty("$INSTANCES.$name.waitDurationInOpenState")).isEqualTo("10s")
        }
    }

    @Test
    fun 기본_설정의_열림_대기는_30초로_남는다() {
        assertThat(properties.getProperty("$DEFAULT_CONFIG.waitDurationInOpenState")).isEqualTo("30s")
    }

    companion object {
        private const val DEFAULT_CONFIG = "resilience4j.circuitbreaker.configs.default"
        private const val INSTANCES = "resilience4j.circuitbreaker.instances"
        private val GRPC_QUERY_INSTANCES = listOf("order-query", "cart-query", "order-item-query")
    }
}
