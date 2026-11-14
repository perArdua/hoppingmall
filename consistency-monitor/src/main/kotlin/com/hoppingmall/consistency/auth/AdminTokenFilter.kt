package com.hoppingmall.consistency.auth

import io.jsonwebtoken.Claims
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Metrics
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.util.UrlPathHelper

@Component
class AdminTokenFilter(
    @Value("\${jwt.secret}") secret: String,
    private val redisTemplate: StringRedisTemplate? = null,
    private val meterRegistry: MeterRegistry = Metrics.globalRegistry
) : OncePerRequestFilter() {

    private val parser = Jwts.parserBuilder()
        .setSigningKey(Keys.hmacShaKeyFor(secret.toByteArray()))
        .build()

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        val path = UrlPathHelper.defaultInstance.getPathWithinApplication(request)
        return path == ACTUATOR_PATH || path.startsWith("$ACTUATOR_PATH/")
    }

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        val token = resolveToken(request)
        val claims = token?.let(::parseAccessToken)
        when {
            token == null || claims == null || claims.subject.isNullOrBlank() -> response.status = HttpStatus.UNAUTHORIZED.value()
            isLoggedOut(token) -> response.status = HttpStatus.UNAUTHORIZED.value()
            claims[ROLE_CLAIM] != ADMIN_ROLE -> response.status = HttpStatus.FORBIDDEN.value()
            else -> {
                request.setAttribute(ADMIN_ID_ATTRIBUTE, claims.subject)
                chain.doFilter(request, response)
            }
        }
    }

    private fun isLoggedOut(token: String): Boolean {
        val template = redisTemplate ?: return false
        return try {
            template.hasKey(BLACKLIST_KEY_PREFIX + token)
        } catch (e: RuntimeException) {
            log.warn("로그아웃 토큰 Redis 조회 실패, 요청은 통과시킴: {}", e.message)
            meterRegistry.counter(BLACKLIST_REDIS_FAILURE_METRIC).increment()
            false
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
        private val log = LoggerFactory.getLogger(AdminTokenFilter::class.java)
        private const val ACTUATOR_PATH = "/actuator"
        private const val BLACKLIST_KEY_PREFIX = "blacklist:"
        private const val BLACKLIST_REDIS_FAILURE_METRIC = "consistency.jwt.blacklist.redis.failures"
        private const val BEARER_PREFIX = "Bearer "
        private const val TOKEN_TYPE_CLAIM = "typ"
        private const val ACCESS_TOKEN_TYPE = "access"
        private const val ROLE_CLAIM = "role"
        private const val ADMIN_ROLE = "ADMIN"
    }
}
