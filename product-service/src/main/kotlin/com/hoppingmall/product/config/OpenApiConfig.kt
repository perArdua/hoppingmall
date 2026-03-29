package com.hoppingmall.product.config

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
                    .title("Product Service API")
                    .version("v1")
                    .description("상품, 카테고리, 재고, 리뷰, 위시리스트 관리 API")
            )
    }
}
