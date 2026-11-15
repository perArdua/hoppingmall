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
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import java.net.InetSocketAddress
import java.util.Date
import java.util.concurrent.CopyOnWriteArrayList

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "jwt.secret=${ConsistencyMonitorRouteTest.SECRET}",
        "gateway.rate-limit.enabled=false"
    ]
)
@DisplayName("게이트웨이를 거치는 정합성 위반 조회·해결")
@DisplayNameGeneration(ReplaceUnderscores::class)
class ConsistencyMonitorRouteTest {

    companion object {
        const val SECRET = "test-secret-key-for-testing-purposes-only-32chars!"

        private val received = CopyOnWriteArrayList<Map<String, String?>>()

        private val monitor: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { exchange ->
                received.add(
                    mapOf(
                        "method" to exchange.requestMethod,
                        "path" to exchange.requestURI.path,
                        "query" to exchange.requestURI.query,
                        "authorization" to exchange.requestHeaders.getFirst("Authorization"),
                        "body" to exchange.requestBody.readAllBytes().toString(Charsets.UTF_8)
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
            registry.add("ROUTES_CONSISTENCY_MONITOR") { "http://127.0.0.1:${monitor.address.port}" }
        }

        @JvmStatic
        @AfterAll
        fun stopMonitor() {
            monitor.stop(0)
        }
    }

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @BeforeEach
    fun setUp() {
        received.clear()
    }

    private fun buildToken(subject: String, role: String): String =
        Jwts.builder()
            .setSubject(subject)
            .claim("typ", "access")
            .claim("role", role)
            .setExpiration(Date(System.currentTimeMillis() + 60_000))
            .signWith(Keys.hmacShaKeyFor(SECRET.toByteArray()), SignatureAlgorithm.HS256)
            .compact()

    @Test
    fun ADMIN_토큰으로_위반_목록을_조회하면_모니터에_닿고_토큰이_함께_넘어간다() {
        val token = buildToken("1", "ADMIN")

        webTestClient.get()
            .uri("/api/v1/violations?openOnly=false")
            .header("Authorization", "Bearer $token")
            .exchange()
            .expectStatus().isOk

        assertThat(received).hasSize(1)
        assertThat(received[0]["method"]).isEqualTo("GET")
        assertThat(received[0]["path"]).isEqualTo("/api/v1/violations")
        assertThat(received[0]["query"]).isEqualTo("openOnly=false")
        assertThat(received[0]["authorization"]).isEqualTo("Bearer $token")
    }

    @Test
    fun ADMIN_토큰으로_위반을_해결하면_본문이_모니터에_그대로_닿는다() {
        webTestClient.post()
            .uri("/api/v1/violations/7/resolve")
            .header("Authorization", "Bearer ${buildToken("1", "ADMIN")}")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"resolutionNote":"수동 보정 완료"}""")
            .exchange()
            .expectStatus().isOk

        assertThat(received).hasSize(1)
        assertThat(received[0]["method"]).isEqualTo("POST")
        assertThat(received[0]["path"]).isEqualTo("/api/v1/violations/7/resolve")
        assertThat(received[0]["body"]).isEqualTo("""{"resolutionNote":"수동 보정 완료"}""")
    }

    @ParameterizedTest
    @ValueSource(strings = ["BUYER", "SELLER"])
    fun ADMIN이_아닌_토큰은_403이고_모니터에_닿지_않는다(role: String) {
        webTestClient.get()
            .uri("/api/v1/violations")
            .header("Authorization", "Bearer ${buildToken("42", role)}")
            .exchange()
            .expectStatus().isForbidden

        webTestClient.post()
            .uri("/api/v1/violations/7/resolve")
            .header("Authorization", "Bearer ${buildToken("42", role)}")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"resolutionNote":"x"}""")
            .exchange()
            .expectStatus().isForbidden

        assertThat(received).isEmpty()
    }

    @Test
    fun 토큰_없이_요청하면_401이고_모니터에_닿지_않는다() {
        webTestClient.get()
            .uri("/api/v1/violations")
            .exchange()
            .expectStatus().isUnauthorized

        assertThat(received).isEmpty()
    }
}
