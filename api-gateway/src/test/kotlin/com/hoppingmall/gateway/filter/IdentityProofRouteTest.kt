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
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "jwt.secret=${IdentityProofRouteTest.JWT_SECRET}",
        "gateway.rate-limit.enabled=false"
    ]
)
@DisplayName("게이트웨이가 뒷단에 붙이는 신원 증명 헤더")
@DisplayNameGeneration(ReplaceUnderscores::class)
class IdentityProofRouteTest {

    companion object {
        const val JWT_SECRET = "test-secret-key-for-testing-purposes-only-32chars!"
        private const val PROOF_HEADER = "x-gateway-identity"

        private val identitySecret = UUID.randomUUID().toString()
        private val receivedProofs = CopyOnWriteArrayList<List<String>>()

        private val backend: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { exchange ->
                receivedProofs.add(exchange.requestHeaders[PROOF_HEADER].orEmpty())
                exchange.sendResponseHeaders(200, -1)
                exchange.close()
            }
            start()
        }

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            val backendUrl = "http://127.0.0.1:${backend.address.port}"
            registry.add("ROUTES_USER_SERVICE") { backendUrl }
            registry.add("ROUTES_ORDER_SERVICE") { backendUrl }
            registry.add("gateway.identity.secret") { identitySecret }
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
        receivedProofs.clear()
        whenever(redisTemplate.hasKey(any())).thenReturn(Mono.just(false))
    }

    private fun accessToken(): String =
        Jwts.builder()
            .setSubject("7")
            .claim("typ", "access")
            .claim("role", "BUYER")
            .setIssuedAt(Date())
            .setExpiration(Date(System.currentTimeMillis() + 60_000))
            .signWith(Keys.hmacShaKeyFor(JWT_SECRET.toByteArray()), SignatureAlgorithm.HS256)
            .compact()

    @Test
    fun 토큰을_검증한_요청은_뒷단에_신원_증명_헤더를_붙여_보낸다() {
        webTestClient.get()
            .uri("/api/v1/orders")
            .header("Authorization", "Bearer ${accessToken()}")
            .exchange()
            .expectStatus().isOk

        assertThat(receivedProofs).containsExactly(listOf(identitySecret))
    }

    @Test
    fun 클라이언트가_보낸_신원_증명_헤더는_토큰을_검증한_요청에서도_게이트웨이_값으로_바뀐다() {
        webTestClient.get()
            .uri("/api/v1/orders")
            .header("Authorization", "Bearer ${accessToken()}")
            .header(PROOF_HEADER, "forged")
            .exchange()
            .expectStatus().isOk

        assertThat(receivedProofs).containsExactly(listOf(identitySecret))
    }

    @Test
    fun 공개_경로에서는_클라이언트가_보낸_신원_증명_헤더와_신원_헤더가_뒷단에_가지_않는다() {
        webTestClient.post()
            .uri("/api/v1/users/login")
            .header("x-user-id", "1")
            .header("x-user-role", "ADMIN")
            .header(PROOF_HEADER, "forged")
            .exchange()
            .expectStatus().isOk

        assertThat(receivedProofs).containsExactly(emptyList())
    }
}
