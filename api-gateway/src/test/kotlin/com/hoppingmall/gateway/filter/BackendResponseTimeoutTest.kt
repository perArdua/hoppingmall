package com.hoppingmall.gateway.filter

import com.sun.net.httpserver.HttpServer
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import io.jsonwebtoken.security.Keys
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpStatus
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import java.net.InetSocketAddress
import java.time.Duration
import java.util.Date
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "jwt.secret=${BackendResponseTimeoutTest.SECRET}",
        "gateway.rate-limit.enabled=false"
    ]
)
@DisplayName("뒷단 응답 타임아웃")
@DisplayNameGeneration(ReplaceUnderscores::class)
class BackendResponseTimeoutTest {

    companion object {
        const val SECRET = "test-secret-key-for-testing-purposes-only-32chars!"

        private val release = CountDownLatch(1)

        private val backend: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            executor = Executors.newCachedThreadPool()
            createContext("/") { exchange ->
                release.await()
                exchange.sendResponseHeaders(200, -1)
                exchange.close()
            }
            start()
        }

        @JvmStatic
        @DynamicPropertySource
        fun routes(registry: DynamicPropertyRegistry) {
            registry.add("ROUTES_ORDER_SERVICE") { "http://127.0.0.1:${backend.address.port}" }
        }

        @JvmStatic
        @AfterAll
        fun stopBackend() {
            release.countDown()
            backend.stop(0)
        }
    }

    @Autowired
    private lateinit var webTestClient: WebTestClient

    private fun buildToken(subject: String, role: String): String =
        Jwts.builder()
            .setSubject(subject)
            .claim("role", role)
            .setExpiration(Date(System.currentTimeMillis() + 60_000))
            .signWith(Keys.hmacShaKeyFor(SECRET.toByteArray()), SignatureAlgorithm.HS256)
            .compact()

    @Test
    fun 뒷단이_응답하지_않으면_10초가_지나_504로_끝난다() {
        val client = webTestClient.mutate().responseTimeout(Duration.ofSeconds(20)).build()
        val startedAt = System.nanoTime()

        val status = client.get()
            .uri("/api/v1/orders")
            .header("Authorization", "Bearer ${buildToken("42", "BUYER")}")
            .exchange()
            .expectBody()
            .returnResult()
            .status
        val elapsed = Duration.ofNanos(System.nanoTime() - startedAt)

        assertThat(status).isEqualTo(HttpStatus.GATEWAY_TIMEOUT)
        assertThat(elapsed).isBetween(Duration.ofSeconds(10), Duration.ofSeconds(15))
    }
}
