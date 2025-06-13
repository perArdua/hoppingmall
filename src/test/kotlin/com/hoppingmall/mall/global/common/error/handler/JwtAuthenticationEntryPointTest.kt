package com.hoppingmall.mall.global.common.error.handler

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.AuthenticationException
import kotlin.test.assertEquals

class JwtAuthenticationEntryPointTest {

    private val entryPoint = JwtAuthenticationEntryPoint(ObjectMapper())

    @Test
    fun `인가되지 않은 요청에 대해 401 상태 코드 반환`() {
        val request = MockHttpServletRequest()
        val response = MockHttpServletResponse()

        entryPoint.commence(request, response, object : AuthenticationException("Unauthorized") {})

        assertEquals(401, response.status)
    }
}