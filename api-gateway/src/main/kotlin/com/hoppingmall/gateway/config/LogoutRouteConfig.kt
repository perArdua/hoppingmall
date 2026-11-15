package com.hoppingmall.gateway.config

import org.springframework.cloud.gateway.config.GatewayProperties
import org.springframework.cloud.gateway.route.RouteLocator
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod

@Configuration
class LogoutRouteConfig {

    @Bean
    fun logoutRoutes(builder: RouteLocatorBuilder, gatewayProperties: GatewayProperties): RouteLocator {
        val userServiceUri = gatewayProperties.routes.firstOrNull { it.id == USER_SERVICE_PUBLIC_ROUTE_ID }?.uri
            ?: return builder.routes().build()

        return builder.routes()
            .route(ROUTE_ID) { route ->
                route.order(ROUTE_ORDER)
                    .method(HttpMethod.POST)
                    .and().path(LOGOUT_PATH)
                    .uri(userServiceUri)
            }
            .build()
    }

    companion object {
        const val ROUTE_ID = "user-service-logout"
        const val USER_SERVICE_PUBLIC_ROUTE_ID = "user-service-public"
        const val LOGOUT_PATH = "/api/v1/auth/logout"
        private const val ROUTE_ORDER = -1
    }
}
