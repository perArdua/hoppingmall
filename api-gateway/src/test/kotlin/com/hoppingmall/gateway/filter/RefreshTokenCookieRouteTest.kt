package com.hoppingmall.gateway.filter

import com.sun.net.httpserver.HttpServer
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import io.jsonwebtoken.security.Keys
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.data.redis.core.ReactiveValueOperations
import org.springframework.http.HttpMethod
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.reactive.server.WebTestClient
import reactor.core.publisher.Mono
import java.net.InetSocketAddress
import java.time.Duration
import java.util.Date
import java.util.concurrent.CopyOnWriteArrayList

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["jwt.secret=${RefreshTokenCookieRouteTest.SECRET}"]
)
@DisplayName("리프레시 토큰 쿠키가 게이트웨이를 지나는 길")
@DisplayNameGeneration(ReplaceUnderscores::class)
class RefreshTokenCookieRouteTest {

    companion object {
        const val SECRET = "test-secret-key-for-testing-purposes-only-32chars!"
        private const val FRONT_ORIGIN = "http://localhost:3000"
        private const val BACKEND_SET_COOKIE = "hm_rt=rotated; Path=/api/v1/auth; Max-Age=86400; Secure; HttpOnly; SameSite=Strict"

        private val backendCookies = CopyOnWriteArrayList<String>()

        private val backend: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { exchange ->
                backendCookies.add(exchange.requestHeaders.getFirst("Cookie").orEmpty())
                exchange.responseHeaders.add("Set-Cookie", BACKEND_SET_COOKIE)
                exchange.sendResponseHeaders(200, -1)
                exchange.close()
            }
            start()
        }

        @JvmStatic
        @DynamicPropertySource
        fun routes(registry: DynamicPropertyRegistry) {
            registry.add("ROUTES_USER_SERVICE") { "http://127.0.0.1:${backend.address.port}" }
        }

        @JvmStatic
        @AfterAll
        fun stopBackend() {
            backend.stop(0)
        }
    }

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @MockitoBean
    private lateinit var redisTemplate: ReactiveStringRedisTemplate

    private val incrementedKeys = CopyOnWriteArrayList<String>()

    @BeforeEach
    fun setUp() {
        backendCookies.clear()
        val valueOps: ReactiveValueOperations<String, String> = mock()
        whenever(redisTemplate.opsForValue()).thenReturn(valueOps)
        whenever(valueOps.increment(any<String>())).thenAnswer { invocation ->
            incrementedKeys.add(invocation.getArgument(0))
            Mono.just(1L)
        }
        whenever(redisTemplate.expire(any(), any<Duration>())).thenReturn(Mono.just(true))
    }

    private fun refreshToken(subject: String): String =
        Jwts.builder()
            .setSubject(subject)
            .claim("typ", "refresh")
            .setExpiration(Date(System.currentTimeMillis() + 60_000))
            .signWith(Keys.hmacShaKeyFor(SECRET.toByteArray()), SignatureAlgorithm.HS256)
            .compact()

    @Test
    fun 쿠키_방식_재발급의_요청_제한은_쿠키_토큰의_사용자로_센다() {
        webTestClient.post().uri("/api/v1/auth/refresh")
            .header("X-Token-Transport", "cookie")
            .header("Origin", FRONT_ORIGIN)
            .cookie("hm_rt", refreshToken("42"))
            .exchange()
            .expectStatus().isOk

        assertThat(incrementedKeys).singleElement().asString().startsWith("rl:refresh:user:42:")
    }

    @Test
    fun 쿠키_방식_로그아웃의_요청_제한도_쿠키_토큰의_사용자로_센다() {
        webTestClient.post().uri("/api/v1/auth/logout")
            .header("X-Token-Transport", "cookie")
            .header("Origin", FRONT_ORIGIN)
            .cookie("hm_rt", refreshToken("42"))
            .exchange()
            .expectStatus().isOk

        assertThat(incrementedKeys).singleElement().asString().startsWith("rl:logout:user:42:")
    }

    @Test
    fun 헤더_없이_쿠키만_오면_쿠키를_보지_않고_클라이언트_IP로_센다() {
        webTestClient.post().uri("/api/v1/auth/refresh")
            .cookie("hm_rt", refreshToken("42"))
            .exchange()

        assertThat(incrementedKeys).singleElement().asString().startsWith("rl:refresh:ip:")
    }

    @Test
    fun 쿠키는_user_service로_그대로_넘어가고_user_service의_Set_Cookie는_지워지지_않고_돌아온다() {
        val token = refreshToken("42")

        webTestClient.post().uri("/api/v1/auth/refresh")
            .header("X-Token-Transport", "cookie")
            .header("Origin", FRONT_ORIGIN)
            .cookie("hm_rt", token)
            .exchange()
            .expectStatus().isOk
            .expectHeader().valueEquals("Set-Cookie", BACKEND_SET_COOKIE)
            .expectHeader().valueEquals("Access-Control-Allow-Origin", FRONT_ORIGIN)
            .expectHeader().valueEquals("Access-Control-Allow-Credentials", "true")

        assertThat(backendCookies).singleElement().asString().contains("hm_rt=$token")
    }

    @Test
    fun 프론트_출처의_사전_요청은_자격_증명과_X_Token_Transport_헤더를_허용한다() {
        webTestClient.method(HttpMethod.OPTIONS).uri("/api/v1/auth/refresh")
            .header("Origin", FRONT_ORIGIN)
            .header("Access-Control-Request-Method", "POST")
            .header("Access-Control-Request-Headers", "x-token-transport")
            .exchange()
            .expectStatus().isOk
            .expectHeader().valueEquals("Access-Control-Allow-Origin", FRONT_ORIGIN)
            .expectHeader().valueEquals("Access-Control-Allow-Credentials", "true")
            .expectHeader().value("Access-Control-Allow-Headers") { assertThat(it).containsIgnoringCase("x-token-transport") }
    }

    @Test
    fun 다른_출처의_사전_요청은_거절해_쿠키_방식_헤더를_보낼_수_없다() {
        webTestClient.method(HttpMethod.OPTIONS).uri("/api/v1/auth/refresh")
            .header("Origin", "https://evil.example.net")
            .header("Access-Control-Request-Method", "POST")
            .header("Access-Control-Request-Headers", "x-token-transport")
            .exchange()
            .expectStatus().isForbidden
            .expectHeader().doesNotExist("Access-Control-Allow-Origin")

        assertThat(backendCookies).isEmpty()
    }
}
