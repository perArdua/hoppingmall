package com.hoppingmall.user.config

import com.hoppingmall.user.auth.RefreshTokenCookieProperties
import com.hoppingmall.user.auth.cookie.RefreshTokenCookieOriginInterceptor
import com.hoppingmall.user.auth.cookie.RefreshTokenCookies
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
@EnableConfigurationProperties(RefreshTokenCookieProperties::class)
class RefreshTokenCookieConfig(
    private val refreshTokenCookies: RefreshTokenCookies
) : WebMvcConfigurer {

    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(RefreshTokenCookieOriginInterceptor(refreshTokenCookies))
            .addPathPatterns(RefreshTokenCookieOriginInterceptor.PATHS)
    }
}
