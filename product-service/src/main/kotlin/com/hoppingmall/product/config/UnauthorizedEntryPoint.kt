package com.hoppingmall.product.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.hoppingmall.common.ApiResponse
import com.hoppingmall.common.CommonErrorCode
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets

@Component
class UnauthorizedEntryPoint(
    private val objectMapper: ObjectMapper
) : AuthenticationEntryPoint {

    override fun commence(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authException: AuthenticationException
    ) {
        response.status = CommonErrorCode.UNAUTHORIZED.status.value()
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.characterEncoding = StandardCharsets.UTF_8.name()
        objectMapper.writeValue(response.writer, ApiResponse.failure<Unit>(CommonErrorCode.UNAUTHORIZED))
    }
}
