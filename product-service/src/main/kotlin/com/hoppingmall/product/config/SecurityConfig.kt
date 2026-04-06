package com.hoppingmall.product.config

import com.hoppingmall.common.config.BaseSecurityConfig
import com.hoppingmall.common.config.InternalTokenFilter
import com.hoppingmall.common.config.GatewayHeaderAuthenticationFilter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.web.SecurityFilterChain

@Configuration
@EnableWebSecurity
class SecurityConfig(
    gatewayHeaderAuthenticationFilter: GatewayHeaderAuthenticationFilter,
    internalTokenFilter: InternalTokenFilter
) : BaseSecurityConfig(gatewayHeaderAuthenticationFilter, internalTokenFilter) {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        return configureBase(http).build()
    }
}
