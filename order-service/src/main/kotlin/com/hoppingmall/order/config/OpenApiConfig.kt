package com.hoppingmall.order.config

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
                    .title("Order Service API")
                    .version("v1")
                    .description("주문, 장바구니, 배송, 환불 관리 API")
            )
    }
}
