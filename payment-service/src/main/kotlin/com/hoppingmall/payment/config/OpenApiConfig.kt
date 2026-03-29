package com.hoppingmall.payment.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {
    @Bean
    fun openAPI(): OpenAPI {
        return OpenAPI()
            .info(
                Info()
                    .title("Payment Service API")
                    .version("v1")
                    .description("결제, 포인트, 쿠폰, DLQ 관리 API")
            )
    }
}
