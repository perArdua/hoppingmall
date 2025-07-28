package com.hoppingmall.mall.global.common.error.handler

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.*
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.AuthenticationException
import kotlin.test.assertEquals

@DisplayName("JwtAuthenticationEntryPoint")
@DisplayNameGeneration(ReplaceUnderscores::class)
class JwtAuthenticationEntryPointTest {

    private val entryPoint = JwtAuthenticationEntryPoint(ObjectMapper())

    @Nested
    @DisplayName("commence")
    inner class Commence {
        @Test
        fun 인가되지_않은_요청에_대해_401_상태_코드_반환() {
            val request = MockHttpServletRequest()
            val response = MockHttpServletResponse()

            entryPoint.commence(request, response, object : AuthenticationException("Unauthorized") {})

            assertEquals(401, response.status)
        }
    }
}