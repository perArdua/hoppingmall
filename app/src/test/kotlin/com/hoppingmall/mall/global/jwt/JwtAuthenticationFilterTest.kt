package com.hoppingmall.mall.global.jwt

import com.hoppingmall.mall.global.auth.UserPrincipal
import com.hoppingmall.mall.global.auth.domain.repository.AccessTokenBlacklistRepository
import jakarta.servlet.FilterChain
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.kotlin.whenever
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import kotlin.test.assertNull
import kotlin.test.assertTrue

@DisplayName("JwtAuthenticationFilter")
@DisplayNameGeneration(ReplaceUnderscores::class)
class JwtAuthenticationFilterTest {

    private val tokenProvider: TokenProvider = mock()
    private val accessTokenBlacklistRepository: AccessTokenBlacklistRepository = mock()
    private val filter = JwtAuthenticationFilter(tokenProvider, accessTokenBlacklistRepository)

    @Nested
    @DisplayName("doFilterInternal")
    inner class DoFilterInternal {

        @Test
        fun 유효한_토큰이_있을_경우_SecurityContext에_인증_정보가_저장된다() {
            val token = "valid.jwt.token"
            val request = MockHttpServletRequest().apply {
                addHeader("Authorization", "Bearer $token")
            }
            val response = MockHttpServletResponse()
            val filterChain: FilterChain = mock()
            val userPrincipal: UserPrincipal = mock()

            whenever(tokenProvider.validateToken(token)).thenReturn(true)
            whenever(accessTokenBlacklistRepository.exists(token)).thenReturn(false)
            whenever(tokenProvider.getUserPrincipal(token)).thenReturn(userPrincipal)

            filter.doFilter(request, response, filterChain)

            val authentication = SecurityContextHolder.getContext().authentication
            assertTrue(authentication?.principal === userPrincipal)
            verify(filterChain).doFilter(request, response)
        }

        @Test
        fun 블랙리스트에_등록된_토큰이면_인증_정보가_저장되지_않는다() {
            val token = "blacklisted.jwt.token"
            val request = MockHttpServletRequest().apply {
                addHeader("Authorization", "Bearer $token")
            }
            val response = MockHttpServletResponse()
            val filterChain: FilterChain = mock()

            SecurityContextHolder.clearContext()
            whenever(tokenProvider.validateToken(token)).thenReturn(true)
            whenever(accessTokenBlacklistRepository.exists(token)).thenReturn(true)

            filter.doFilter(request, response, filterChain)

            val authentication = SecurityContextHolder.getContext().authentication
            assertNull(authentication)
            verify(filterChain).doFilter(request, response)
        }
    }
}
