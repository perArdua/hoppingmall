package com.hoppingmall.user.auth

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.hoppingmall.user.common.enums.Role
import com.hoppingmall.user.domain.Seller
import com.hoppingmall.user.domain.repository.SellerRepository
import com.hoppingmall.user.domain.repository.UserRepository
import com.hoppingmall.user.dto.request.SellerApprovalRequest
import com.hoppingmall.user.dto.request.SignUpRequest
import com.hoppingmall.user.service.AdminCommandService
import com.hoppingmall.user.service.UserCommandService
import com.hoppingmall.user.support.MySqlContainerSupport
import com.hoppingmall.user.support.RedisContainerSupport
import jakarta.servlet.http.Cookie
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import java.time.Instant
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = [
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration",
    "spring.main.allow-bean-definition-overriding=true",
    "spring.profiles.active=test",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "auth.refresh-cookie.allowed-origins=https://shop.example.com"
])
@DisplayName("리프레시 토큰 쿠키 — 실제 MySQL·Redis")
@DisplayNameGeneration(ReplaceUnderscores::class)
class RefreshTokenCookieMySqlRedisIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var userCommandService: UserCommandService

    @Autowired
    private lateinit var adminCommandService: AdminCommandService

    @Autowired
    private lateinit var sellerRepository: SellerRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var tokenProvider: TokenProvider

    @Test
    fun 쿠키_방식으로_로그인하면_hm_rt가_httpOnly_Secure_SameSite_Strict_인증_경로로_나가고_본문에_리프레시_토큰이_없다() {
        val response = cookieLogin(newAccount())

        val setCookie = refreshCookieHeader(response)
        assertThat(response.status).isEqualTo(200)
        assertThat(setCookie).startsWith("hm_rt=")
            .contains("Path=/api/v1/auth", "Max-Age=86400", "Secure", "HttpOnly", "SameSite=Strict")
        assertThat(data(response).has("refresh_token")).isFalse()
        assertThat(data(response).path("access_token").asText()).isNotBlank()
    }

    @Test
    fun 헤더_없이_로그인하면_지금처럼_본문에_리프레시_토큰이_오고_쿠키는_없다() {
        val account = newAccount()

        val response = mockMvc.perform(loginRequest(account)).andReturn().response

        assertThat(response.status).isEqualTo(200)
        assertThat(response.getHeaders("Set-Cookie")).isEmpty()
        assertThat(data(response).path("refresh_token").asText()).isNotBlank()
    }

    @Test
    fun 쿠키로_재발급하면_본문_없이_새_액세스_토큰과_바뀐_쿠키를_받는다() {
        val loginCookie = refreshCookieValue(cookieLogin(newAccount()))
        다음_초까지_기다린다()

        val response = cookieRefresh(loginCookie)

        assertThat(response.status).isEqualTo(200)
        assertThat(data(response).has("refresh_token")).isFalse()
        assertThat(data(response).path("access_token").asText()).isNotBlank()
        assertThat(refreshCookieValue(response)).isNotBlank().isNotEqualTo(loginCookie)
    }

    @Test
    fun 허용되지_않은_Origin에서_온_쿠키_재발급은_403이고_세션은_그대로다() {
        val loginCookie = refreshCookieValue(cookieLogin(newAccount()))

        val rejected = cookieRefresh(loginCookie, origin = "https://evil.example.net")

        assertThat(rejected.status).isEqualTo(403)
        assertThat(rejected.getHeaders("Set-Cookie")).isEmpty()
        assertThat(cookieRefresh(loginCookie).status).isEqualTo(200)
    }

    @Test
    fun Origin이_없으면_Referer의_출처로_판단하고_둘_다_없으면_403이다() {
        val loginCookie = refreshCookieValue(cookieLogin(newAccount()))

        val noOrigin = mockMvc.perform(
            post(REFRESH).header(TRANSPORT, "cookie").cookie(Cookie("hm_rt", loginCookie))
        ).andReturn().response
        val allowedReferer = mockMvc.perform(
            post(REFRESH).header(TRANSPORT, "cookie").header("Referer", "https://shop.example.com/cart?x=1")
                .cookie(Cookie("hm_rt", loginCookie))
        ).andReturn().response

        assertThat(noOrigin.status).isEqualTo(403)
        assertThat(allowedReferer.status).isEqualTo(200)
    }

    @Test
    fun 사용자_헤더_없이_쿠키만_실린_교차_출처_요청은_쿠키를_읽지_않아_세션이_바뀌지_않는다() {
        val loginCookie = refreshCookieValue(cookieLogin(newAccount()))

        val forged = mockMvc.perform(
            post(REFRESH).header("Origin", "https://evil.example.net").cookie(Cookie("hm_rt", loginCookie))
        ).andReturn().response

        assertThat(forged.status).isEqualTo(400)
        assertThat(forged.getHeaders("Set-Cookie")).isEmpty()
        assertThat(cookieRefresh(loginCookie).status).isEqualTo(200)
    }

    @Test
    fun 쿠키로_로그아웃하면_쿠키가_지워지고_그_세션은_더_재발급되지_않는다() {
        val loginCookie = refreshCookieValue(cookieLogin(newAccount()))

        val logout = mockMvc.perform(cookieRequest(post(LOGOUT), loginCookie)).andReturn().response

        assertThat(logout.status).isEqualTo(204)
        assertThat(refreshCookieHeader(logout)).startsWith("hm_rt=;").contains("Max-Age=0", "Path=/api/v1/auth")
        assertThat(cookieRefresh(loginCookie).status).isEqualTo(401)
    }

    @Test
    fun 두_번_바뀐_옛_쿠키로_재발급하면_401이고_쿠키를_지운다() {
        val loginCookie = refreshCookieValue(cookieLogin(newAccount()))
        다음_초까지_기다린다()
        val once = refreshCookieValue(cookieRefresh(loginCookie))
        다음_초까지_기다린다()
        refreshCookieValue(cookieRefresh(once))

        val replayed = cookieRefresh(loginCookie)

        assertThat(replayed.status).isEqualTo(401)
        assertThat(refreshCookieHeader(replayed)).contains("Max-Age=0")
    }

    @Test
    fun 판매자_승인_뒤_쿠키로_재발급한_액세스_토큰에는_SELLER가_실린다() {
        val account = newAccount()
        val login = cookieLogin(account)
        val seller = sellerRepository.save(
            Seller.create(userRepository.findById(account.userId).get(), "${(100..999).random()}-${(10..99).random()}-${(10000..99999).random()}")
        )

        adminCommandService.updateSellerApprovalStatus(seller.id!!, SellerApprovalRequest("APPROVED"))
        val refreshed = cookieRefresh(refreshCookieValue(login))

        assertThat(tokenProvider.getUserRoleFromToken(data(login).path("access_token").asText())).isEqualTo(Role.BUYER)
        assertThat(tokenProvider.getUserRoleFromToken(data(refreshed).path("access_token").asText())).isEqualTo(Role.SELLER)
    }

    private fun cookieLogin(account: Account): MockHttpServletResponse =
        mockMvc.perform(loginRequest(account).header(TRANSPORT, "cookie").header("Origin", ALLOWED_ORIGIN))
            .andReturn().response

    private fun loginRequest(account: Account): MockHttpServletRequestBuilder =
        post("/api/v1/users/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"email":"${account.email}","password":"$PASSWORD"}""")

    private fun cookieRefresh(cookie: String, origin: String = ALLOWED_ORIGIN): MockHttpServletResponse =
        mockMvc.perform(cookieRequest(post(REFRESH), cookie, origin)).andReturn().response

    private fun cookieRequest(
        builder: MockHttpServletRequestBuilder,
        cookie: String,
        origin: String = ALLOWED_ORIGIN
    ): MockHttpServletRequestBuilder =
        builder.header(TRANSPORT, "cookie").header("Origin", origin).cookie(Cookie("hm_rt", cookie))

    private fun refreshCookieHeader(response: MockHttpServletResponse): String =
        response.getHeaders("Set-Cookie").single { it.startsWith("hm_rt=") }

    private fun refreshCookieValue(response: MockHttpServletResponse): String =
        refreshCookieHeader(response).substringAfter("hm_rt=").substringBefore(';')

    private fun data(response: MockHttpServletResponse): JsonNode =
        objectMapper.readTree(response.contentAsString).path("data")

    private fun 다음_초까지_기다린다() {
        val second = Instant.now().epochSecond
        while (Instant.now().epochSecond == second) {
            Thread.sleep(20)
        }
    }

    private fun newAccount(): Account {
        val email = "cookie-${UUID.randomUUID().toString().take(12)}@example.com"
        val signUp = userCommandService.signUp(SignUpRequest(email = email, password = PASSWORD, name = "쿠키사용자"))
        return Account(signUp.id, email)
    }

    private data class Account(val userId: Long, val email: String)

    companion object {
        private const val PASSWORD = "Password1234"
        private const val REFRESH = "/api/v1/auth/refresh"
        private const val LOGOUT = "/api/v1/auth/logout"
        private const val TRANSPORT = "X-Token-Transport"
        private const val ALLOWED_ORIGIN = "https://shop.example.com"

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            MySqlContainerSupport.register(registry, "user_refresh_cookie_test")
            registry.add("spring.data.redis.host") { RedisContainerSupport.host }
            registry.add("spring.data.redis.port") { RedisContainerSupport.port }
        }
    }
}
