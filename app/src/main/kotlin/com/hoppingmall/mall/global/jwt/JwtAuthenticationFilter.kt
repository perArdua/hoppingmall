package com.hoppingmall.mall.global.jwt

import com.hoppingmall.mall.global.auth.UserPrincipal
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class JwtAuthenticationFilter(
    private val jwtTokenParser: JwtTokenParser
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val token = resolveToken(request)

        if (token != null) {
            val userId = jwtTokenParser.parseUserId(token)
            val role = jwtTokenParser.parseRole(token)

            if (userId != null && role != null) {
                val userPrincipal = UserPrincipal.of(userId, role)

                val authentication = UsernamePasswordAuthenticationToken(
                    userPrincipal,
                    null,
                    userPrincipal.authorities
                ).apply {
                    details = WebAuthenticationDetailsSource().buildDetails(request)
                }

                SecurityContextHolder.getContext().authentication = authentication
            }
        }

        filterChain.doFilter(request, response)
    }

    private fun resolveToken(request: HttpServletRequest): String? {
        val bearerToken = request.getHeader("Authorization") ?: return null
        return if (bearerToken.startsWith("Bearer ")) bearerToken.substring(7) else null
    }
}
