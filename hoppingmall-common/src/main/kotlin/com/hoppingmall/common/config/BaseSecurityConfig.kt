package com.hoppingmall.common.config

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.AuthenticationProvider
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.Authentication
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

abstract class BaseSecurityConfig(
    private val gatewayHeaderAuthenticationFilter: GatewayHeaderAuthenticationFilter,
    private val internalTokenFilter: InternalTokenFilter?
) {

    protected fun configureBase(http: HttpSecurity): HttpSecurity {
        http
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers("/actuator/**").permitAll()
                    .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html").permitAll()
                configureInternalEndpoints(auth)
                configureServiceEndpoints(auth)
                auth.anyRequest().authenticated()
            }

        internalTokenFilter?.let {
            http.addFilterBefore(it, UsernamePasswordAuthenticationFilter::class.java)
        }
        http.addFilterBefore(gatewayHeaderAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)

        return http
    }

    @Bean
    @ConditionalOnMissingBean(
        AuthenticationManager::class,
        AuthenticationProvider::class,
        UserDetailsService::class
    )
    open fun usernamePasswordRejectingAuthenticationProvider(): AuthenticationProvider =
        object : AuthenticationProvider {
            override fun authenticate(authentication: Authentication): Authentication =
                throw BadCredentialsException("Username and password authentication is not supported")

            override fun supports(authentication: Class<*>): Boolean = true
        }

    protected open fun configureInternalEndpoints(
        auth: AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry
    ) {
        auth.requestMatchers("/internal/**").permitAll()
    }

    protected open fun configureServiceEndpoints(
        auth: AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry
    ) {
    }
}
