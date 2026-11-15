package com.hoppingmall.gateway.config

import com.hoppingmall.gateway.filter.JwtValidationGatewayFilterFactory
import org.springframework.cloud.gateway.route.RouteLocator
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import org.springframework.core.env.Profiles

@Configuration
class ConsistencyMonitorRouteConfig {

    @Bean
    fun consistencyMonitorRoutes(
        builder: RouteLocatorBuilder,
        jwtValidation: JwtValidationGatewayFilterFactory,
        environment: Environment
    ): RouteLocator {
        val adminOnly = jwtValidation.apply(
            JwtValidationGatewayFilterFactory.Config().apply { requiredRole = ADMIN_ROLE }
        )
        return builder.routes()
            .route(ROUTE_ID) { route ->
                route.path(*VIOLATION_PATHS)
                    .filters { it.filter(adminOnly) }
                    .uri(monitorUri(environment))
            }
            .build()
    }

    companion object {
        const val ROUTE_ID = "consistency-monitor-violations"
        private const val ADMIN_ROLE = "ADMIN"
        private const val URI_PROPERTY = "ROUTES_CONSISTENCY_MONITOR"
        private val VIOLATION_PATHS = arrayOf("/api/v1/violations", "/api/v1/violations/**")
        private val PROFILE_DEFAULT_URIS = listOf(
            "docker" to "http://consistency-monitor:8087",
            "k8s" to "http://consistency-monitor.hoppingmall.svc.cluster.local:8087"
        )
        private const val LOCAL_URI = "http://localhost:8087"

        fun monitorUri(environment: Environment): String =
            environment.getProperty(URI_PROPERTY)
                ?: PROFILE_DEFAULT_URIS.firstOrNull { (profile, _) -> environment.acceptsProfiles(Profiles.of(profile)) }?.second
                ?: LOCAL_URI
    }
}
