package com.hoppingmall.notification.config

import com.hoppingmall.common.config.BaseSecurityConfig
import com.hoppingmall.common.config.GatewayHeaderAuthenticationFilter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.web.SecurityFilterChain

@Configuration
@EnableWebSecurity
class SecurityConfig(
    gatewayHeaderAuthenticationFilter: GatewayHeaderAuthenticationFilter
) : BaseSecurityConfig(gatewayHeaderAuthenticationFilter, null) {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        return configureBase(http).build()
    }

    override fun configureInternalEndpoints(
        auth: AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry
    ) {
        auth.requestMatchers("/internal/**").denyAll()
    }
}
