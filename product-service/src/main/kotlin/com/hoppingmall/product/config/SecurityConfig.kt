package com.hoppingmall.product.config

import com.hoppingmall.common.config.BaseSecurityConfig
import com.hoppingmall.common.config.InternalTokenFilter
import com.hoppingmall.common.config.GatewayHeaderAuthenticationFilter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer
import org.springframework.security.web.SecurityFilterChain

@Configuration
@EnableWebSecurity
class SecurityConfig(
    gatewayHeaderAuthenticationFilter: GatewayHeaderAuthenticationFilter,
    internalTokenFilter: InternalTokenFilter
) : BaseSecurityConfig(gatewayHeaderAuthenticationFilter, internalTokenFilter) {

    override fun configureServiceEndpoints(
        auth: AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry
    ) {
        auth
            .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
            .requestMatchers("/api/v1/files/upload").hasAnyRole("SELLER", "ADMIN")
            .requestMatchers("/api/v1/products/images/upload").hasAnyRole("SELLER", "ADMIN")
            .requestMatchers(HttpMethod.POST, "/api/v1/products").hasAnyRole("SELLER", "ADMIN")
            .requestMatchers(HttpMethod.PUT, "/api/v1/products/{productId}").hasAnyRole("SELLER", "ADMIN")
            .requestMatchers(HttpMethod.DELETE, "/api/v1/products/{productId}").hasAnyRole("SELLER", "ADMIN")
            .requestMatchers(HttpMethod.POST, "/api/v1/products/bulk/validate").hasAnyRole("SELLER", "ADMIN")
            .requestMatchers(HttpMethod.POST, "/api/v1/products/bulk/import").hasAnyRole("SELLER", "ADMIN")
            .requestMatchers(HttpMethod.POST, "/api/v1/inventories").hasAnyRole("SELLER", "ADMIN")
            .requestMatchers(HttpMethod.PATCH, "/api/v1/inventories/{productId}").hasAnyRole("SELLER", "ADMIN")
            .requestMatchers(HttpMethod.POST, "/api/v1/categories").hasRole("ADMIN")
            .requestMatchers(HttpMethod.PUT, "/api/v1/categories/{categoryId}").hasRole("ADMIN")
            .requestMatchers(HttpMethod.DELETE, "/api/v1/categories/{categoryId}").hasRole("ADMIN")
    }

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        return configureBase(http).build()
    }
}
