package com.hoppingmall.gateway.filter

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import io.jsonwebtoken.security.Keys
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import java.net.ServerSocket
import java.util.Date

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "jwt.secret=${JwtRouteBackendFailureTest.SECRET}",
        "gateway.rate-limit.enabled=false"
    ]
)
@DisplayName("JWT 라우트의 뒷단 연결 실패 응답")
@DisplayNameGeneration(ReplaceUnderscores::class)
class JwtRouteBackendFailureTest {

    companion object {
        const val SECRET = "test-secret-key-for-testing-purposes-only-32chars!"

        private val closedPort: Int = ServerSocket(0).use { it.localPort }

        @JvmStatic
        @DynamicPropertySource
        fun routes(registry: DynamicPropertyRegistry) {
            registry.add("ROUTES_ORDER_SERVICE") { "http://127.0.0.1:$closedPort" }
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
    fun 유효한_토큰으로_요청했는데_뒷단에_연결할_수_없으면_401이_아니라_5xx로_응답한다() {
        webTestClient.get()
            .uri("/api/v1/orders")
            .header("Authorization", "Bearer ${buildToken("42", "BUYER")}")
            .exchange()
            .expectStatus().is5xxServerError
    }

    @Test
    fun 토큰_없이_요청하면_뒷단_상태와_상관없이_401로_응답한다() {
        webTestClient.get()
            .uri("/api/v1/orders")
            .exchange()
            .expectStatus().isUnauthorized
    }
}
