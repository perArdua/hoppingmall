package com.hoppingmall.user.auth.cookie

import com.hoppingmall.common.ApiResponse
import com.hoppingmall.user.auth.dto.AccessTokenResponse
import com.hoppingmall.user.auth.dto.TokenRefreshResponse
import com.hoppingmall.user.dto.response.SignInResponse
import org.springframework.core.MethodParameter
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseCookie
import org.springframework.http.converter.HttpMessageConverter
import org.springframework.http.server.ServerHttpRequest
import org.springframework.http.server.ServerHttpResponse
import org.springframework.http.server.ServletServerHttpRequest
import org.springframework.http.server.ServletServerHttpResponse
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice

@ControllerAdvice
class RefreshTokenCookieResponseAdvice(
    private val cookies: RefreshTokenCookies
) : ResponseBodyAdvice<Any> {

    override fun supports(returnType: MethodParameter, converterType: Class<out HttpMessageConverter<*>>): Boolean = true

    override fun beforeBodyWrite(
        body: Any?,
        returnType: MethodParameter,
        selectedContentType: MediaType,
        selectedConverterType: Class<out HttpMessageConverter<*>>,
        request: ServerHttpRequest,
        response: ServerHttpResponse
    ): Any? {
        val servletRequest = (request as? ServletServerHttpRequest)?.servletRequest ?: return body
        val cookieTransport = cookies.isCookieTransport(servletRequest)
        if (cookieTransport && body is ApiResponse<*>) {
            val issued = when (val data = body.data) {
                is SignInResponse -> data.accessToken to data.refreshToken
                is TokenRefreshResponse -> data.accessToken to data.refreshToken
                else -> null
            }
            if (issued != null) {
                setCookie(response, cookies.issue(issued.second))
                return ApiResponse.success(AccessTokenResponse(issued.first))
            }
        }
        if ((cookieTransport || cookies.read(servletRequest) != null) && shouldClear(servletRequest.requestURI, response)) {
            setCookie(response, cookies.clear())
        }
        return body
    }

    private fun shouldClear(path: String, response: ServerHttpResponse): Boolean = when (path) {
        LOGOUT_PATH, LOGOUT_ALL_PATH -> true
        REFRESH_PATH -> (response as? ServletServerHttpResponse)?.servletResponse?.status == UNAUTHORIZED
        else -> false
    }

    private fun setCookie(response: ServerHttpResponse, cookie: ResponseCookie) {
        response.headers.add(HttpHeaders.SET_COOKIE, cookie.toString())
    }

    companion object {
        private const val REFRESH_PATH = "/api/v1/auth/refresh"
        private const val LOGOUT_PATH = "/api/v1/auth/logout"
        private const val LOGOUT_ALL_PATH = "/api/v1/auth/logout-all"
        private const val UNAUTHORIZED = 401
    }
}
