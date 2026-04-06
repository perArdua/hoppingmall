package com.hoppingmall.common.config

import com.hoppingmall.common.UserPrincipal
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class GatewayHeaderAuthenticationFilter : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val userId = request.getHeader(USER_ID_HEADER)?.toLongOrNull()
        val role = request.getHeader(USER_ROLE_HEADER) ?: DEFAULT_ROLE

        if (userId != null) {
            val userPrincipal = UserPrincipal.of(userId, role)
            val authentication = UsernamePasswordAuthenticationToken(
                userPrincipal, null, userPrincipal.authorities
            )
            SecurityContextHolder.getContext().authentication = authentication
        }

        filterChain.doFilter(request, response)
    }

    companion object {
        const val USER_ID_HEADER = MdcFilter.USER_ID_HEADER
        const val USER_ROLE_HEADER = "x-user-role"
        private const val DEFAULT_ROLE = "BUYER"
    }
}
