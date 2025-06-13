package com.hoppingmall.mall.global.common.error.handler

import com.fasterxml.jackson.databind.ObjectMapper
import com.hoppingmall.mall.global.common.error.code.CommonErrorCode
import com.hoppingmall.mall.global.common.response.ApiResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.stereotype.Component

@Component
class JwtAuthenticationEntryPoint(
    private val objectMapper: ObjectMapper
) : AuthenticationEntryPoint {

    override fun commence(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authException: AuthenticationException
    ) {
        response.status = HttpServletResponse.SC_UNAUTHORIZED
        response.contentType = "application/json;charset=UTF-8"

        val errorResponse = ApiResponse.failure<Unit>(CommonErrorCode.UNAUTHORIZED)
        response.writer.write(objectMapper.writeValueAsString(errorResponse))
    }
}
