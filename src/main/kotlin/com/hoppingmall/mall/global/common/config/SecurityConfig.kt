package com.hoppingmall.mall.global.common.config

import jakarta.servlet.Filter
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

@Configuration
class SecurityConfig(
    private val jwtAuthenticationFilter: Filter,
    private val jwtAuthenticationEntryPoint: AuthenticationEntryPoint
) {

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .exceptionHandling { it.authenticationEntryPoint(jwtAuthenticationEntryPoint) }
            .authorizeHttpRequests {
                it.requestMatchers("/api/v1/users/signup", "/api/v1/users/login").permitAll()
                it.requestMatchers(
                    HttpMethod.GET,
                    "/api/v1/products",
                    "/api/v1/products/{productId}",
                    "/api/v1/products/seller/{sellerId}"
                ).permitAll()
                it.requestMatchers(
                    HttpMethod.GET,
                    "/api/v1/point-policies/current",
                    "/api/v1/point-policies/{policyId}"
                ).permitAll()

                it.requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                it.requestMatchers("/api/v1/point-policies/**").hasRole("ADMIN")

                it.requestMatchers(
                    HttpMethod.POST,
                    "/api/v1/products",
                    "/api/v1/products/images/upload"
                ).hasRole("SELLER")
                it.requestMatchers(HttpMethod.PUT, "/api/v1/products/{productId}").hasRole("SELLER")
                it.requestMatchers(HttpMethod.DELETE, "/api/v1/products/{productId}").hasRole("SELLER")

                it.requestMatchers("/api/v1/**").authenticated()
                it.anyRequest().denyAll()
            }
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)

        return http.build()
    }

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    @Bean
    fun authenticationManager(authConfig: AuthenticationConfiguration): AuthenticationManager {
        return authConfig.authenticationManager
    }
}
