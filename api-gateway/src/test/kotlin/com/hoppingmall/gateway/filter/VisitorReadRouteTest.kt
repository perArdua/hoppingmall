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
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
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
    properties = ["jwt.secret=${VisitorReadRouteTest.SECRET}"]
)
@DisplayName("로그인하지 않은 방문자의 상품·카테고리·리뷰 조회")
@DisplayNameGeneration(ReplaceUnderscores::class)
class VisitorReadRouteTest {

    companion object {
        const val SECRET = "test-secret-key-for-testing-purposes-only-32chars!"

        private val backendRequests = CopyOnWriteArrayList<Map<String, String?>>()

        private val backend: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { exchange ->
                val headers = exchange.requestHeaders
                backendRequests.add(
                    mapOf(
                        "method" to exchange.requestMethod,
                        "path" to exchange.requestURI.path,
                        "query" to exchange.requestURI.query,
                        "x-user-id" to headers.getFirst("x-user-id"),
                        "x-user-role" to headers.getFirst("x-user-role")
                    )
                )
                exchange.sendResponseHeaders(200, -1)
                exchange.close()
            }
            start()
        }

        @JvmStatic
        @DynamicPropertySource
        fun routes(registry: DynamicPropertyRegistry) {
            registry.add("ROUTES_PRODUCT_SERVICE") { "http://127.0.0.1:${backend.address.port}" }
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
        backendRequests.clear()
        val valueOps: ReactiveValueOperations<String, String> = mock()
        whenever(redisTemplate.opsForValue()).thenReturn(valueOps)
        whenever(valueOps.increment(any<String>())).thenAnswer { invocation ->
            incrementedKeys.add(invocation.getArgument(0))
            Mono.just(1L)
        }
        whenever(redisTemplate.expire(any(), any<Duration>())).thenReturn(Mono.just(true))
        whenever(redisTemplate.hasKey(any())).thenReturn(Mono.just(false))
    }

    private fun buildToken(subject: String, role: String): String =
        Jwts.builder()
            .setSubject(subject)
            .claim("typ", "access")
            .claim("role", role)
            .setExpiration(Date(System.currentTimeMillis() + 60_000))
            .signWith(Keys.hmacShaKeyFor(SECRET.toByteArray()), SignatureAlgorithm.HS256)
            .compact()

    private fun send(method: HttpMethod, path: String, headers: Map<String, String> = emptyMap()): Int =
        webTestClient.method(method)
            .uri(path)
            .headers { httpHeaders -> headers.forEach { (name, value) -> httpHeaders.set(name, value) } }
            .exchange()
            .expectBody()
            .returnResult()
            .status
            .value()

    @ParameterizedTest
    @ValueSource(
        strings = [
            "/api/v1/products",
            "/api/v1/products/7",
            "/api/v1/products/seller/3",
            "/api/v1/products/category/5",
            "/api/v1/products/search",
            "/api/v1/products/7/reviews",
            "/api/v1/categories/root",
            "/api/v1/categories/5",
            "/api/v1/categories/5/sub",
            "/api/v1/reviews/11"
        ]
    )
    fun 토큰_없는_조회는_신원_헤더_없이_상품_서비스에_닿는다(path: String) {
        val status = send(HttpMethod.GET, path)

        assertThat(status).isEqualTo(200)
        assertThat(backendRequests).hasSize(1)
        assertThat(backendRequests[0]["method"]).isEqualTo("GET")
        assertThat(backendRequests[0]["path"]).isEqualTo(path)
        assertThat(backendRequests[0]["x-user-id"]).isNull()
        assertThat(backendRequests[0]["x-user-role"]).isNull()
    }

    @Test
    fun 토큰_없는_조회의_쿼리_문자열은_그대로_넘어간다() {
        val status = send(HttpMethod.GET, "/api/v1/products/search?keyword=shoe&page=1&size=20")

        assertThat(status).isEqualTo(200)
        assertThat(backendRequests).hasSize(1)
        assertThat(backendRequests[0]["query"]).isEqualTo("keyword=shoe&page=1&size=20")
    }

    @Test
    fun 토큰_없는_조회에_클라이언트가_넣은_신원_헤더는_뒷단에_닿지_않는다() {
        val status = send(HttpMethod.GET, "/api/v1/products/7", mapOf("x-user-id" to "1", "x-user-role" to "ADMIN"))

        assertThat(status).isEqualTo(200)
        assertThat(backendRequests).hasSize(1)
        assertThat(backendRequests[0]["x-user-id"]).isNull()
        assertThat(backendRequests[0]["x-user-role"]).isNull()
    }

    @Test
    fun 토큰이_있는_조회는_토큰의_사용자와_역할을_뒷단에_넘긴다() {
        val status = send(
            HttpMethod.GET,
            "/api/v1/products/7",
            mapOf("Authorization" to "Bearer ${buildToken("42", "BUYER")}", "x-user-id" to "999", "x-user-role" to "ADMIN")
        )

        assertThat(status).isEqualTo(200)
        assertThat(backendRequests).hasSize(1)
        assertThat(backendRequests[0]["x-user-id"]).isEqualTo("42")
        assertThat(backendRequests[0]["x-user-role"]).isEqualTo("BUYER")
    }

    @Test
    fun 공개_조회라도_잘못된_토큰을_보내면_401이고_뒷단에_닿지_않는다() {
        val status = send(HttpMethod.GET, "/api/v1/products/7", mapOf("Authorization" to "Bearer not-a-jwt"))

        assertThat(status).isEqualTo(401)
        assertThat(backendRequests).isEmpty()
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "/api/v1/reviews/my",
            "/api/v1/wishlists",
            "/api/v1/wishlists/check/7",
            "/api/v1/products/bulk/1",
            "/api/v1/products/bulk/1/errors",
            "/api/v1/inventories/7",
            "/api/v1/seller/dashboard/overview",
            "/api/v1/products/abc",
            "/api/v1/categories/abc"
        ]
    )
    fun 본인_신원이_필요한_조회는_토큰_없이_401이고_뒷단에_닿지_않는다(path: String) {
        val status = send(HttpMethod.GET, path)

        assertThat(status).isEqualTo(401)
        assertThat(backendRequests).isEmpty()
    }

    @ParameterizedTest
    @CsvSource(
        "POST, /api/v1/products",
        "PUT, /api/v1/products/7",
        "DELETE, /api/v1/products/7",
        "POST, /api/v1/categories",
        "PUT, /api/v1/categories/5",
        "DELETE, /api/v1/categories/5",
        "POST, /api/v1/reviews",
        "PUT, /api/v1/reviews/11",
        "DELETE, /api/v1/reviews/11"
    )
    fun 쓰기는_토큰_없이_401이고_뒷단에_닿지_않는다(method: String, path: String) {
        val status = send(HttpMethod.valueOf(method), path)

        assertThat(status).isEqualTo(401)
        assertThat(backendRequests).isEmpty()
    }

    @Test
    fun 토큰_없는_조회의_요청_제한은_클라이언트_IP로_센다() {
        send(HttpMethod.GET, "/api/v1/products/7", mapOf("x-user-id" to "spoofed"))
        send(HttpMethod.GET, "/api/v1/products/search")

        assertThat(incrementedKeys).hasSize(2)
        assertThat(incrementedKeys[0]).startsWith("rl:global:127.0.0.1:")
        assertThat(incrementedKeys[1]).startsWith("rl:search:127.0.0.1:")
    }
}
