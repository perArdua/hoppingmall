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
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import java.net.InetSocketAddress
import java.util.Date
import java.util.concurrent.CopyOnWriteArrayList

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "jwt.secret=${OperationalRouteAccessTest.SECRET}",
        "gateway.rate-limit.enabled=false"
    ]
)
@DisplayName("게이트웨이를 거치는 운영 API 접근")
@DisplayNameGeneration(ReplaceUnderscores::class)
class OperationalRouteAccessTest {

    companion object {
        const val SECRET = "test-secret-key-for-testing-purposes-only-32chars!"

        private val SERVICES = listOf("USER", "PRODUCT", "ORDER", "PAYMENT", "SETTLEMENT", "NOTIFICATION")

        private val received = CopyOnWriteArrayList<String>()

        private val backends: Map<String, HttpServer> = SERVICES.associateWith { service ->
            HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
                createContext("/") { exchange ->
                    received.add("${service.lowercase()} ${exchange.requestMethod} ${exchange.requestURI.path}")
                    exchange.sendResponseHeaders(200, -1)
                    exchange.close()
                }
                start()
            }
        }

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            backends.forEach { (service, server) ->
                registry.add("ROUTES_${service}_SERVICE") { "http://127.0.0.1:${server.address.port}" }
            }
        }

        @JvmStatic
        @AfterAll
        fun stopBackends() {
            backends.values.forEach { it.stop(0) }
        }
    }

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @BeforeEach
    fun setUp() {
        received.clear()
    }

    private fun token(role: String): String =
        Jwts.builder()
            .setSubject("1")
            .claim("typ", "access")
            .claim("role", role)
            .setIssuedAt(Date())
            .setExpiration(Date(System.currentTimeMillis() + 60_000))
            .signWith(Keys.hmacShaKeyFor(SECRET.toByteArray()), SignatureAlgorithm.HS256)
            .compact()

    @ParameterizedTest
    @ValueSource(strings = ["order", "product", "user", "notification", "payment"])
    fun ADMIN은_서비스_이름을_붙인_DLQ_관리_경로로_그_서비스의_DLQ_관리_API에_닿는다(service: String) {
        webTestClient.get()
            .uri("/api/v1/admin/dlq/$service/messages/order-events?page=0")
            .header("Authorization", "Bearer ${token("ADMIN")}")
            .exchange()
            .expectStatus().isOk

        assertThat(received).containsExactly("$service GET /api/v1/admin/dlq/messages/order-events")
    }

    @ParameterizedTest
    @ValueSource(strings = ["BUYER", "SELLER"])
    fun ADMIN이_아니면_서비스별_DLQ_관리_경로는_403이고_뒷단에_닿지_않는다(role: String) {
        webTestClient.post()
            .uri("/api/v1/admin/dlq/order/retry/order-events")
            .header("Authorization", "Bearer ${token(role)}")
            .exchange()
            .expectStatus().isForbidden

        assertThat(received).isEmpty()
    }

    @Test
    fun 인증_없이_서비스별_DLQ_관리_경로를_부르면_401이고_뒷단에_닿지_않는다() {
        webTestClient.get()
            .uri("/api/v1/admin/dlq/product/stats")
            .exchange()
            .expectStatus().isUnauthorized

        assertThat(received).isEmpty()
    }

    @ParameterizedTest
    @ValueSource(strings = ["ADMIN", "SELLER", "BUYER"])
    fun 서비스_이름이_없는_옛_DLQ_관리_경로는_라우트가_없어_뒷단에_닿지_않는다(role: String) {
        webTestClient.get()
            .uri("/api/v1/admin/dlq/stats")
            .header("Authorization", "Bearer ${token(role)}")
            .exchange()
            .expectStatus().isNotFound

        assertThat(received).isEmpty()
    }

    @ParameterizedTest
    @ValueSource(strings = ["BUYER", "SELLER"])
    fun ADMIN이_아니면_payment_DLQ_관리_경로도_403이고_뒷단에_닿지_않는다(role: String) {
        webTestClient.get()
            .uri("/api/v1/admin/dlq/payment/stats")
            .header("Authorization", "Bearer ${token(role)}")
            .exchange()
            .expectStatus().isForbidden

        assertThat(received).isEmpty()
    }

    @ParameterizedTest
    @CsvSource(
        "/api/v1/admin/product-statistics/products/1, product",
        "/api/v1/admin/coupons, payment",
        "/api/v1/admin/settlements, settlement",
        "/api/v1/admin/sellers/1/approve, user"
    )
    fun ADMIN은_관리자_경로로_그_서비스에_닿는다(path: String, service: String) {
        webTestClient.get()
            .uri(path)
            .header("Authorization", "Bearer ${token("ADMIN")}")
            .exchange()
            .expectStatus().isOk

        assertThat(received).containsExactly("$service GET $path")
    }

    @ParameterizedTest
    @CsvSource(
        "/api/v1/admin/product-statistics/products/1, BUYER",
        "/api/v1/admin/product-statistics/products/1, SELLER",
        "/api/v1/admin/coupons, BUYER",
        "/api/v1/admin/coupons, SELLER",
        "/api/v1/admin/settlements, BUYER",
        "/api/v1/admin/settlements, SELLER",
        "/api/v1/admin/sellers/1/approve, BUYER",
        "/api/v1/admin/sellers/1/approve, SELLER"
    )
    fun ADMIN이_아니면_관리자_경로는_403이고_뒷단에_닿지_않는다(path: String, role: String) {
        webTestClient.get()
            .uri(path)
            .header("Authorization", "Bearer ${token(role)}")
            .exchange()
            .expectStatus().isForbidden

        assertThat(received).isEmpty()
    }

    @Test
    fun 파일_업로드는_product로_간다() {
        webTestClient.post()
            .uri("/api/v1/files/upload")
            .header("Authorization", "Bearer ${token("SELLER")}")
            .exchange()
            .expectStatus().isOk

        assertThat(received).containsExactly("product POST /api/v1/files/upload")
    }

    @Test
    fun 인증_없이_파일_업로드를_부르면_401이고_뒷단에_닿지_않는다() {
        webTestClient.post()
            .uri("/api/v1/files/upload")
            .exchange()
            .expectStatus().isUnauthorized

        assertThat(received).isEmpty()
    }

    @ParameterizedTest
    @ValueSource(strings = ["/api/v1/violations", "/ops/circuitbreakers/reset-all", "/internal/api/v1/orders/1"])
    fun 권한_검사가_없거나_밖에_열지_않는_운영_API는_라우트가_없어_뒷단에_닿지_않는다(path: String) {
        webTestClient.post()
            .uri(path)
            .header("Authorization", "Bearer ${token("ADMIN")}")
            .exchange()
            .expectStatus().isNotFound

        assertThat(received).isEmpty()
    }
}
