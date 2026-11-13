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
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.reactive.server.WebTestClient
import reactor.core.publisher.Mono
import java.net.InetSocketAddress
import java.util.Date
import java.util.concurrent.CopyOnWriteArrayList

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "jwt.secret=${JwtBlacklistRouteTest.SECRET}",
        "gateway.rate-limit.enabled=false"
    ]
)
@DisplayName("게이트웨이 라우트의 로그아웃 토큰 거절")
@DisplayNameGeneration(ReplaceUnderscores::class)
class JwtBlacklistRouteTest {

    companion object {
        const val SECRET = "test-secret-key-for-testing-purposes-only-32chars!"

        private val backendPaths = CopyOnWriteArrayList<String>()

        private val backend: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { exchange ->
                backendPaths.add(exchange.requestURI.path)
                exchange.sendResponseHeaders(200, -1)
                exchange.close()
            }
            start()
        }

        @JvmStatic
        @DynamicPropertySource
        fun routes(registry: DynamicPropertyRegistry) {
            val backendUrl = "http://127.0.0.1:${backend.address.port}"
            registry.add("ROUTES_USER_SERVICE") { backendUrl }
            registry.add("ROUTES_ORDER_SERVICE") { backendUrl }
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

    @BeforeEach
    fun setUp() {
        backendPaths.clear()
    }

    private fun accessToken(subject: String): String =
        Jwts.builder()
            .setSubject(subject)
            .claim("typ", "access")
            .claim("role", "BUYER")
            .setIssuedAt(Date())
            .setExpiration(Date(System.currentTimeMillis() + 60_000))
            .signWith(Keys.hmacShaKeyFor(SECRET.toByteArray()), SignatureAlgorithm.HS256)
            .compact()

    @Test
    fun 로그아웃한_access_token으로_보호된_API를_부르면_401이고_뒷단에_가지_않는다() {
        val token = accessToken("42")
        whenever(redisTemplate.hasKey(any())).thenReturn(Mono.just(false))
        whenever(redisTemplate.hasKey("blacklist:$token")).thenReturn(Mono.just(true))

        webTestClient.get()
            .uri("/api/v1/orders")
            .header("Authorization", "Bearer $token")
            .exchange()
            .expectStatus().isUnauthorized

        assertThat(backendPaths).isEmpty()
    }

    @Test
    fun 로그아웃하지_않은_access_token은_뒷단까지_간다() {
        whenever(redisTemplate.hasKey(any())).thenReturn(Mono.just(false))

        webTestClient.get()
            .uri("/api/v1/orders")
            .header("Authorization", "Bearer ${accessToken("7")}")
            .exchange()
            .expectStatus().isOk

        assertThat(backendPaths).containsExactly("/api/v1/orders")
    }
}
