package com.hoppingmall.gateway.filter

import com.sun.net.httpserver.HttpServer
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import io.jsonwebtoken.security.Keys
import io.micrometer.core.instrument.MeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.util.Date
import java.util.concurrent.CopyOnWriteArrayList

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "jwt.secret=${JwtBlacklistRedisUnavailableTest.SECRET}",
        "gateway.rate-limit.enabled=false"
    ]
)
@DisplayName("로그아웃 토큰 Redis에 연결할 수 없을 때의 게이트웨이 응답")
@DisplayNameGeneration(ReplaceUnderscores::class)
@ExtendWith(OutputCaptureExtension::class)
class JwtBlacklistRedisUnavailableTest {

    companion object {
        const val SECRET = "test-secret-key-for-testing-purposes-only-32chars!"

        private val closedRedisPort: Int = ServerSocket(0).use { it.localPort }

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
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("ROUTES_ORDER_SERVICE") { "http://127.0.0.1:${backend.address.port}" }
            registry.add("spring.data.redis.host") { "127.0.0.1" }
            registry.add("spring.data.redis.port") { closedRedisPort }
        }

        @JvmStatic
        @AfterAll
        fun stopBackend() {
            backend.stop(0)
        }
    }

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @Autowired
    private lateinit var meterRegistry: MeterRegistry

    @BeforeEach
    fun setUp() {
        backendPaths.clear()
    }

    private fun accessToken(): String =
        Jwts.builder()
            .setSubject("42")
            .claim("typ", "access")
            .claim("role", "BUYER")
            .setIssuedAt(Date())
            .setExpiration(Date(System.currentTimeMillis() + 60_000))
            .signWith(Keys.hmacShaKeyFor(SECRET.toByteArray()), SignatureAlgorithm.HS256)
            .compact()

    private fun failureCount(): Double =
        meterRegistry.find("gateway.jwt.blacklist.redis.failures").counters().sumOf { it.count() }

    @Test
    fun Redis에_연결할_수_없으면_유효한_access_token의_요청을_뒷단까지_보내고_실패_지표와_WARN_로그를_남긴다(output: CapturedOutput) {
        val token = accessToken()
        val before = failureCount()

        webTestClient.get()
            .uri("/api/v1/orders")
            .header("Authorization", "Bearer $token")
            .exchange()
            .expectStatus().isOk

        assertThat(backendPaths).containsExactly("/api/v1/orders")
        assertThat(failureCount()).isEqualTo(before + 1)
        val warnLines = output.out.lines().filter { it.contains("WARN") && it.contains("JwtValidationGatewayFilterFactory") }
        assertThat(warnLines).isNotEmpty()
        assertThat(warnLines).noneMatch { it.contains(token) }
    }
}
