package com.hoppingmall.mall.global.jwt

import com.hoppingmall.mall.global.auth.UserPrincipal
import jakarta.servlet.FilterChain
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.kotlin.whenever
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import kotlin.test.assertTrue

class JwtAuthenticationFilterTest {

    private val tokenProvider: TokenProvider = mock()
    private val filter = JwtAuthenticationFilter(tokenProvider)

    @Test
    fun `유효한 토큰이 있을 경우 SecurityContext에 인증 정보가 저장된다`() {
        // given
        val token = "valid.jwt.token"
        val request = MockHttpServletRequest().apply {
            addHeader("Authorization", "Bearer $token")
        }
        val response = MockHttpServletResponse()
        val filterChain: FilterChain = mock()
        val userPrincipal: UserPrincipal = mock()

        whenever(tokenProvider.validateToken(token)).thenReturn(true)
        whenever(tokenProvider.getUserPrincipal(token)).thenReturn(userPrincipal)

        // when
        filter.doFilter(request, response, filterChain)

        // then
        val authentication = SecurityContextHolder.getContext().authentication
        assertTrue(authentication?.principal === userPrincipal)
        verify(filterChain).doFilter(request, response)
    }
}
