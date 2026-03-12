package com.hoppingmall.mall.global.common.config

import jakarta.servlet.Filter
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
@EnableConfigurationProperties(CorsProperties::class)
class SecurityConfig(
    private val jwtAuthenticationFilter: Filter,
    private val jwtAuthenticationEntryPoint: AuthenticationEntryPoint,
    private val corsProperties: CorsProperties
) {

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .cors { it.configurationSource(corsConfigurationSource()) }
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .exceptionHandling { it.authenticationEntryPoint(jwtAuthenticationEntryPoint) }
            .authorizeHttpRequests {
                it.requestMatchers("/api/v1/users/signup", "/api/v1/users/login").permitAll()
                it.requestMatchers(
                    HttpMethod.GET,
                    "/api/v1/products",
                    "/api/v1/products/{productId}",
                    "/api/v1/products/seller/{sellerId}",
                    "/api/v1/products/category/{categoryId}",
                    "/api/v1/products/search"
                ).permitAll()
                it.requestMatchers(
                    HttpMethod.GET,
                    "/api/v1/point-policies/current",
                    "/api/v1/point-policies/{policyId}"
                ).permitAll()
                it.requestMatchers(HttpMethod.GET, "/api/v1/inventories/{productId}").permitAll()
                it.requestMatchers(
                    HttpMethod.GET,
                    "/api/v1/categories/root",
                    "/api/v1/categories/{categoryId}",
                    "/api/v1/categories/{categoryId}/sub"
                ).permitAll()
                it.requestMatchers(
                    HttpMethod.GET,
                    "/api/v1/reviews/{reviewId}",
                    "/api/v1/products/{productId}/reviews"
                ).permitAll()
                it.requestMatchers(HttpMethod.GET, "/api/v1/coupons/available").permitAll()

                it.requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                it.requestMatchers("/api/v1/point-policies/**").hasRole("ADMIN")
                it.requestMatchers("/api/v1/categories/**").hasRole("ADMIN")
                it.requestMatchers(HttpMethod.GET, "/api/v1/memberships/users/{userId}").hasRole("ADMIN")

                it.requestMatchers(
                    HttpMethod.POST,
                    "/api/v1/products",
                    "/api/v1/products/images/upload",
                    "/api/v1/products/bulk/validate",
                    "/api/v1/products/bulk/import"
                ).hasRole("SELLER")
                it.requestMatchers(
                    HttpMethod.GET,
                    "/api/v1/products/bulk/{jobId}",
                    "/api/v1/products/bulk/{jobId}/errors"
                ).hasRole("SELLER")
                it.requestMatchers(HttpMethod.PUT, "/api/v1/products/{productId}").hasRole("SELLER")
                it.requestMatchers(HttpMethod.DELETE, "/api/v1/products/{productId}").hasRole("SELLER")
                it.requestMatchers(HttpMethod.POST, "/api/v1/inventories").hasRole("SELLER")
                it.requestMatchers(HttpMethod.PATCH, "/api/v1/inventories/{productId}").hasRole("SELLER")
                it.requestMatchers(HttpMethod.POST, "/api/v1/files/upload").hasRole("SELLER")
                it.requestMatchers(HttpMethod.POST, "/api/v1/shipping").hasRole("SELLER")
                it.requestMatchers(HttpMethod.PATCH, "/api/v1/shipping/{shippingId}/status").hasRole("SELLER")

                it.requestMatchers(HttpMethod.PATCH, "/api/v1/refunds/{refundId}/approve", "/api/v1/refunds/{refundId}/reject")
                    .hasAnyRole("SELLER", "ADMIN")
                it.requestMatchers(HttpMethod.GET, "/api/v1/refunds/seller").hasRole("SELLER")

                it.requestMatchers("/api/v1/**").authenticated()
                it.anyRequest().denyAll()
            }
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)

        return http.build()
    }

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration = CorsConfiguration()
        configuration.allowedOrigins = corsProperties.allowedOrigins
        configuration.allowedMethods = corsProperties.allowedMethods
        configuration.allowedHeaders = corsProperties.allowedHeaders
        configuration.allowCredentials = corsProperties.allowCredentials
        configuration.maxAge = corsProperties.maxAge

        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", configuration)
        return source
    }

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    @Bean
    fun authenticationManager(authConfig: AuthenticationConfiguration): AuthenticationManager {
        return authConfig.authenticationManager
    }
}
