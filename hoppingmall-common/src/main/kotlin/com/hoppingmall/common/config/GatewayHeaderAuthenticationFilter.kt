package com.hoppingmall.common.config

import com.hoppingmall.common.UserPrincipal
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.env.Environment
import org.springframework.core.env.Profiles
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class GatewayHeaderAuthenticationFilter @Autowired constructor(
    @Value("\${gateway.identity.secret:}") identitySecret: String,
    environment: Environment?
) : OncePerRequestFilter() {

    constructor() : this("", null)

    private val identityProof = GatewayIdentityProof(identitySecret)

    init {
        check(identityProof.enabled || environment?.acceptsProfiles(ENFORCED_PROFILES) != true) {
            "docker·k8s 프로필에서는 GATEWAY_IDENTITY_SECRET 환경 변수가 있어야 합니다"
        }
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val userId = request.getHeader(USER_ID_HEADER)?.toLongOrNull()
        val role = request.getHeader(USER_ROLE_HEADER) ?: DEFAULT_ROLE

        if (userId != null && identityProof.isFromGateway(request)) {
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
        const val IDENTITY_PROOF_HEADER = GatewayIdentityProof.HEADER
        private const val DEFAULT_ROLE = "BUYER"
        private val ENFORCED_PROFILES = Profiles.of("docker | k8s")
    }
}
