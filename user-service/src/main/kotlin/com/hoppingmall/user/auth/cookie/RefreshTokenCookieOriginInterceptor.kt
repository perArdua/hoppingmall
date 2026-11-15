package com.hoppingmall.user.auth.cookie

import com.hoppingmall.common.BusinessException
import com.hoppingmall.common.CommonErrorCode
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.web.servlet.HandlerInterceptor

class RefreshTokenCookieOriginInterceptor(
    private val cookies: RefreshTokenCookies
) : HandlerInterceptor {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        if (cookies.isCookieTransport(request) && !cookies.isAllowedOrigin(request)) {
            log.warn("허용 목록에 없는 출처의 쿠키 방식 요청을 거절함: path={}", request.requestURI)
            throw BusinessException(CommonErrorCode.FORBIDDEN)
        }
        return true
    }

    companion object {
        val PATHS = listOf("/api/v1/users/login", "/api/v1/auth/**")
    }
}
