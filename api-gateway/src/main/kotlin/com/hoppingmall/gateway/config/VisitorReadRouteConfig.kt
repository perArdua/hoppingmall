package com.hoppingmall.gateway.config

import org.springframework.cloud.gateway.config.GatewayProperties
import org.springframework.cloud.gateway.route.RouteLocator
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod

@Configuration
class VisitorReadRouteConfig {

    @Bean
    fun visitorReadRoutes(builder: RouteLocatorBuilder, gatewayProperties: GatewayProperties): RouteLocator {
        val productServiceUri = gatewayProperties.routes.firstOrNull { it.id == PRODUCT_SERVICE_ROUTE_ID }?.uri
            ?: return builder.routes().build()

        return builder.routes()
            .route(ROUTE_ID) { route ->
                route.order(ROUTE_ORDER)
                    .method(HttpMethod.GET)
                    .and().path(*VISITOR_READ_PATHS.toTypedArray())
                    .and().not { it.header(HttpHeaders.AUTHORIZATION) }
                    .uri(productServiceUri)
            }
            .build()
    }

    companion object {
        const val ROUTE_ID = "product-service-visitor-read"
        const val PRODUCT_SERVICE_ROUTE_ID = "product-service-products"
        private const val ROUTE_ORDER = -1

        val VISITOR_READ_PATHS = listOf(
            "/api/v1/products",
            "/api/v1/products/{productId:\\d+}",
            "/api/v1/products/seller/{sellerId:\\d+}",
            "/api/v1/products/category/{categoryId:\\d+}",
            "/api/v1/products/search",
            "/api/v1/products/{productId:\\d+}/reviews",
            "/api/v1/categories/root",
            "/api/v1/categories/{categoryId:\\d+}",
            "/api/v1/categories/{categoryId:\\d+}/sub",
            "/api/v1/reviews/{reviewId:\\d+}"
        )
    }
}
