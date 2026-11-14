package com.hoppingmall.consistency.auth

import io.jsonwebtoken.Claims
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.util.UrlPathHelper

@Component
class AdminTokenFilter(@Value("\${jwt.secret}") secret: String) : OncePerRequestFilter() {

    private val parser = Jwts.parserBuilder()
        .setSigningKey(Keys.hmacShaKeyFor(secret.toByteArray()))
        .build()

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        val path = UrlPathHelper.defaultInstance.getPathWithinApplication(request)
        return path == ACTUATOR_PATH || path.startsWith("$ACTUATOR_PATH/")
    }

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        val claims = resolveToken(request)?.let(::parseAccessToken)
        when {
            claims == null || claims.subject.isNullOrBlank() -> response.status = HttpStatus.UNAUTHORIZED.value()
            claims[ROLE_CLAIM] != ADMIN_ROLE -> response.status = HttpStatus.FORBIDDEN.value()
            else -> {
                request.setAttribute(ADMIN_ID_ATTRIBUTE, claims.subject)
                chain.doFilter(request, response)
            }
        }
    }

    private fun resolveToken(request: HttpServletRequest): String? =
        request.getHeader(HttpHeaders.AUTHORIZATION)
            ?.takeIf { it.startsWith(BEARER_PREFIX) }
            ?.substring(BEARER_PREFIX.length)
            ?.takeIf { it.isNotBlank() }

    private fun parseAccessToken(token: String): Claims? {
        val claims = try {
            parser.parseClaimsJws(token).body
        } catch (e: JwtException) {
            return null
        } catch (e: IllegalArgumentException) {
            return null
        }
        val type = claims[TOKEN_TYPE_CLAIM]
        val role = claims[ROLE_CLAIM] as? String
        return claims.takeIf { (type == null || type == ACCESS_TOKEN_TYPE) && !role.isNullOrBlank() }
    }

    companion object {
        const val ADMIN_ID_ATTRIBUTE = "consistency.adminId"
        private const val ACTUATOR_PATH = "/actuator"
        private const val BEARER_PREFIX = "Bearer "
        private const val TOKEN_TYPE_CLAIM = "typ"
        private const val ACCESS_TOKEN_TYPE = "access"
        private const val ROLE_CLAIM = "role"
        private const val ADMIN_ROLE = "ADMIN"
    }
}
