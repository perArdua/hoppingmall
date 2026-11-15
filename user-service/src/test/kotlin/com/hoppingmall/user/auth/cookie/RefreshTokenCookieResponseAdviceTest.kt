package com.hoppingmall.user.auth.cookie

import com.hoppingmall.common.ApiResponse
import com.hoppingmall.common.BusinessException
import com.hoppingmall.common.CommonErrorCode
import com.hoppingmall.user.auth.JwtProperties
import com.hoppingmall.user.auth.RefreshTokenCookieProperties
import com.hoppingmall.user.auth.dto.AccessTokenResponse
import com.hoppingmall.user.auth.dto.TokenRefreshResponse
import com.hoppingmall.user.dto.response.SignInResponse
import jakarta.servlet.http.Cookie
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.springframework.core.MethodParameter
import org.springframework.http.MediaType
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.http.server.ServerHttpRequest
import org.springframework.http.server.ServletServerHttpRequest
import org.springframework.http.server.ServletServerHttpResponse
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

@DisplayName("리프레시 토큰 쿠키 응답 처리")
@DisplayNameGeneration(ReplaceUnderscores::class)
class RefreshTokenCookieResponseAdviceTest {

    private val cookies = RefreshTokenCookies(
        RefreshTokenCookieProperties(listOf("https://shop.example.com")),
        JwtProperties().apply { refreshExpirationMs = 60_000 }
    )
    private val advice = RefreshTokenCookieResponseAdvice(cookies)
    private val interceptor = RefreshTokenCookieOriginInterceptor(cookies)
    private val returnType = mock<MethodParameter>()

    @Test
    fun 모든_반환_형식에_적용된다() {
        assertThat(advice.supports(returnType, MappingJackson2HttpMessageConverter::class.java)).isTrue()
    }

    @Test
    fun 쿠키_방식_재발급_응답은_쿠키로_옮기고_본문에는_액세스_토큰만_남긴다() {
        val (request, response) = exchange("/api/v1/auth/refresh", transport = "cookie")

        val body = write(ApiResponse.success(TokenRefreshResponse("access", "refresh")), request, response)

        assertThat(body).isEqualTo(ApiResponse.success(AccessTokenResponse("access")))
        assertThat(setCookies(response)).singleElement().asString().startsWith("hm_rt=refresh;")
    }

    @Test
    fun 쿠키_방식_로그인_응답도_쿠키로_옮긴다() {
        val (request, response) = exchange("/api/v1/users/login", transport = "cookie")

        val body = write(ApiResponse.success(SignInResponse("access", "refresh")), request, response)

        assertThat(body).isEqualTo(ApiResponse.success(AccessTokenResponse("access")))
        assertThat(setCookies(response)).singleElement().asString().startsWith("hm_rt=refresh;")
    }

    @Test
    fun 헤더가_없으면_토큰_응답을_그대로_둔다() {
        val (request, response) = exchange("/api/v1/auth/refresh")
        val original = ApiResponse.success(TokenRefreshResponse("access", "refresh"))

        assertThat(write(original, request, response)).isSameAs(original)
        assertThat(setCookies(response)).isEmpty()
    }

    @Test
    fun 전체_로그아웃은_쿠키가_실려_있으면_헤더가_없어도_쿠키를_지운다() {
        val (request, response) = exchange("/api/v1/auth/logout-all", cookie = "refresh")

        write(ApiResponse.success(Unit), request, response)

        assertThat(setCookies(response)).singleElement().asString().startsWith("hm_rt=;").contains("Max-Age=0")
    }

    @Test
    fun 쿠키도_헤더도_없는_로그아웃은_Set_Cookie를_보내지_않는다() {
        val (request, response) = exchange("/api/v1/auth/logout")

        write(ApiResponse.success(Unit), request, response)

        assertThat(setCookies(response)).isEmpty()
    }

    @Test
    fun 재발급_실패가_401이_아니면_쿠키를_지우지_않는다() {
        val (request, response) = exchange("/api/v1/auth/refresh", transport = "cookie", status = 403)

        write(ApiResponse.failure<Unit>(CommonErrorCode.FORBIDDEN), request, response)

        assertThat(setCookies(response)).isEmpty()
    }

    @Test
    fun 다른_경로의_쿠키_방식_응답은_건드리지_않는다() {
        val (request, response) = exchange("/api/v1/users/me", transport = "cookie")
        val original = ApiResponse.success("profile")

        assertThat(write(original, request, response)).isSameAs(original)
        assertThat(setCookies(response)).isEmpty()
    }

    @Test
    fun 서블릿_요청이_아니면_본문을_그대로_둔다() {
        val original = ApiResponse.success(TokenRefreshResponse("access", "refresh"))
        val response = ServletServerHttpResponse(MockHttpServletResponse())

        val body = advice.beforeBodyWrite(
            original, returnType, MediaType.APPLICATION_JSON, MappingJackson2HttpMessageConverter::class.java,
            mock<ServerHttpRequest>(), response
        )

        assertThat(body).isSameAs(original)
    }

    @Test
    fun 허용_목록에_없는_출처의_쿠키_방식_요청은_403으로_막고_헤더_없는_요청은_출처를_보지_않는다() {
        val forbidden = MockHttpServletRequest("POST", "/api/v1/auth/refresh").apply {
            addHeader("X-Token-Transport", "cookie")
            addHeader("Origin", "https://evil.example.net")
        }
        val bodyMode = MockHttpServletRequest("POST", "/api/v1/auth/refresh").apply {
            addHeader("Origin", "https://evil.example.net")
        }

        assertThatThrownBy { interceptor.preHandle(forbidden, MockHttpServletResponse(), Any()) }
            .isInstanceOfSatisfying(BusinessException::class.java) {
                assertThat(it.errorCode).isEqualTo(CommonErrorCode.FORBIDDEN)
            }
        assertThat(interceptor.preHandle(bodyMode, MockHttpServletResponse(), Any())).isTrue()
    }

    private fun exchange(
        path: String,
        transport: String? = null,
        cookie: String? = null,
        status: Int = 200
    ): Pair<MockHttpServletRequest, MockHttpServletResponse> {
        val request = MockHttpServletRequest("POST", path).apply {
            transport?.let { addHeader("X-Token-Transport", it) }
            cookie?.let { setCookies(Cookie("hm_rt", it)) }
        }
        return request to MockHttpServletResponse().apply { this.status = status }
    }

    private fun write(body: Any, request: MockHttpServletRequest, response: MockHttpServletResponse): Any? {
        val serverResponse = ServletServerHttpResponse(response)
        val written = advice.beforeBodyWrite(
            body, returnType, MediaType.APPLICATION_JSON, MappingJackson2HttpMessageConverter::class.java,
            ServletServerHttpRequest(request), serverResponse
        )
        serverResponse.flush()
        return written
    }

    private fun setCookies(response: MockHttpServletResponse): List<String> = response.getHeaders("Set-Cookie")
}
