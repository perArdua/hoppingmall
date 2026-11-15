package com.hoppingmall.user.auth.cookie

import com.hoppingmall.user.auth.JwtProperties
import com.hoppingmall.user.auth.RefreshTokenCookieProperties
import jakarta.servlet.http.Cookie
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest

@DisplayName("리프레시 토큰 쿠키 규칙")
@DisplayNameGeneration(ReplaceUnderscores::class)
class RefreshTokenCookiesTest {

    private val cookies = RefreshTokenCookies(
        RefreshTokenCookieProperties(listOf("https://Shop.Example.com/", " http://localhost:3000 ")),
        JwtProperties().apply { refreshExpirationMs = 86_400_000 }
    )

    @Test
    fun 허용_목록의_Origin은_대소문자와_끝_빗금을_가리지_않고_통과한다() {
        assertThat(cookies.isAllowedOrigin(request(origin = "https://shop.example.com"))).isTrue()
        assertThat(cookies.isAllowedOrigin(request(origin = "HTTPS://SHOP.EXAMPLE.COM/"))).isTrue()
        assertThat(cookies.isAllowedOrigin(request(origin = "http://localhost:3000"))).isTrue()
    }

    @Test
    fun 목록에_없는_Origin_포트가_다른_Origin_null_Origin은_거절한다() {
        assertThat(cookies.isAllowedOrigin(request(origin = "https://evil.example.net"))).isFalse()
        assertThat(cookies.isAllowedOrigin(request(origin = "http://localhost:3001"))).isFalse()
        assertThat(cookies.isAllowedOrigin(request(origin = "https://shop.example.com.evil.net"))).isFalse()
        assertThat(cookies.isAllowedOrigin(request(origin = "null"))).isFalse()
    }

    @Test
    fun Origin이_없으면_Referer의_출처만_떼어_비교한다() {
        assertThat(cookies.isAllowedOrigin(request(referer = "http://localhost:3000/orders/1?tab=a"))).isTrue()
        assertThat(cookies.isAllowedOrigin(request(referer = "https://evil.example.net/https://shop.example.com"))).isFalse()
        assertThat(cookies.isAllowedOrigin(request(referer = "not a uri"))).isFalse()
        assertThat(cookies.isAllowedOrigin(request(referer = "/relative/path"))).isFalse()
    }

    @Test
    fun Origin과_Referer가_모두_없으면_거절한다() {
        assertThat(cookies.isAllowedOrigin(request())).isFalse()
    }

    @Test
    fun Origin이_있으면_Referer가_허용_목록이어도_Origin으로_판단한다() {
        assertThat(cookies.isAllowedOrigin(request(origin = "https://evil.example.net", referer = "https://shop.example.com/"))).isFalse()
    }

    @Test
    fun 쿠키_방식은_X_Token_Transport가_cookie일_때만이고_대소문자는_가리지_않는다() {
        assertThat(cookies.isCookieTransport(request(transport = "Cookie"))).isTrue()
        assertThat(cookies.isCookieTransport(request(transport = " cookie "))).isTrue()
        assertThat(cookies.isCookieTransport(request(transport = "body"))).isFalse()
        assertThat(cookies.isCookieTransport(request())).isFalse()
    }

    @Test
    fun hm_rt_쿠키만_읽고_빈_값은_없는_것으로_본다() {
        assertThat(cookies.read(request(cookie = Cookie("hm_rt", "token")))).isEqualTo("token")
        assertThat(cookies.read(request(cookie = Cookie("hm_rt", " ")))).isNull()
        assertThat(cookies.read(request(cookie = Cookie("other", "token")))).isNull()
        assertThat(cookies.read(request())).isNull()
    }

    @Test
    fun 발급_쿠키는_httpOnly_Secure_SameSite_Strict_인증_경로이고_리프레시_수명만큼_산다() {
        val issued = cookies.issue("token").toString()

        assertThat(issued).startsWith("hm_rt=token;")
            .contains("Path=/api/v1/auth", "Max-Age=86400", "Secure", "HttpOnly", "SameSite=Strict")
    }

    @Test
    fun 지우는_쿠키는_같은_이름_경로에_Max_Age_0이다() {
        val cleared = cookies.clear().toString()

        assertThat(cleared).startsWith("hm_rt=;").contains("Path=/api/v1/auth", "Max-Age=0", "Secure", "HttpOnly", "SameSite=Strict")
    }

    @Test
    fun 허용_목록에_와일드카드나_빈_목록은_쓸_수_없다() {
        assertThatThrownBy { RefreshTokenCookieProperties(listOf("https://*.example.com")) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { RefreshTokenCookieProperties(listOf("*")) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { RefreshTokenCookieProperties(listOf(" ")) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun 허용_목록_기본값은_로컬_프론트_하나다() {
        assertThat(RefreshTokenCookieProperties().allowedOrigins).containsExactly("http://localhost:3000")
    }

    private fun request(
        origin: String? = null,
        referer: String? = null,
        transport: String? = null,
        cookie: Cookie? = null
    ): MockHttpServletRequest = MockHttpServletRequest("POST", "/api/v1/auth/refresh").apply {
        origin?.let { addHeader("Origin", it) }
        referer?.let { addHeader("Referer", it) }
        transport?.let { addHeader("X-Token-Transport", it) }
        cookie?.let { setCookies(it) }
    }
}
