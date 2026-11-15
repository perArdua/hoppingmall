package com.hoppingmall.user.auth.cookie

import com.hoppingmall.user.auth.JwtProperties
import com.hoppingmall.user.auth.RefreshTokenCookieProperties
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseCookie
import org.springframework.stereotype.Component
import java.net.URI
import java.time.Duration

@Component
class RefreshTokenCookies(
    private val properties: RefreshTokenCookieProperties,
    private val jwtProperties: JwtProperties
) {

    fun isCookieTransport(request: HttpServletRequest): Boolean = isCookieTransport(request.getHeader(TRANSPORT_HEADER))

    fun read(request: HttpServletRequest): String? =
        request.cookies?.firstOrNull { it.name == COOKIE_NAME }?.value?.takeIf { it.isNotBlank() }

    fun issue(token: String): ResponseCookie = base(token).maxAge(Duration.ofMillis(jwtProperties.refreshExpirationMs)).build()

    fun clear(): ResponseCookie = base("").maxAge(Duration.ZERO).build()

    fun isAllowedOrigin(request: HttpServletRequest): Boolean {
        val origin = request.getHeader(HttpHeaders.ORIGIN)?.takeIf { it.isNotBlank() }
            ?: request.getHeader(HttpHeaders.REFERER)?.let { originOf(it) }
            ?: return false
        return origin.trim().trimEnd('/').lowercase() in properties.allowedOrigins
    }

    private fun originOf(referer: String): String? {
        val uri = runCatching { URI(referer.trim()) }.getOrNull() ?: return null
        val scheme = uri.scheme ?: return null
        val host = uri.host ?: return null
        return if (uri.port == -1) "$scheme://$host" else "$scheme://$host:${uri.port}"
    }

    private fun base(value: String): ResponseCookie.ResponseCookieBuilder =
        ResponseCookie.from(COOKIE_NAME, value)
            .httpOnly(true)
            .secure(true)
            .sameSite(SAME_SITE)
            .path(COOKIE_PATH)

    companion object {
        const val COOKIE_NAME = "hm_rt"
        const val COOKIE_PATH = "/api/v1/auth"
        const val TRANSPORT_HEADER = "X-Token-Transport"
        private const val COOKIE_TRANSPORT = "cookie"
        private const val SAME_SITE = "Strict"

        fun isCookieTransport(headerValue: String?): Boolean =
            headerValue?.trim().equals(COOKIE_TRANSPORT, ignoreCase = true)
    }
}
