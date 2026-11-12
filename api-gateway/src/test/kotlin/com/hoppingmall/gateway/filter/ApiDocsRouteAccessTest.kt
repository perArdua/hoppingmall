package com.hoppingmall.gateway.filter

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
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
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import java.net.InetSocketAddress
import java.util.Date
import java.util.concurrent.CopyOnWriteArrayList

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "jwt.secret=${ApiDocsRouteAccessTest.SECRET}",
        "gateway.rate-limit.enabled=false"
    ]
)
@DisplayName("게이트웨이를 거치는 서비스 API 문서 접근")
@DisplayNameGeneration(ReplaceUnderscores::class)
class ApiDocsRouteAccessTest {

    companion object {
        const val SECRET = "test-secret-key-for-testing-purposes-only-32chars!"

        private val API_DOCS = """
            {
              "openapi": "3.1.0",
              "paths": {
                "/api/v1/orders": {
                  "post": {
                    "requestBody": {"content": {"application/json": {"schema": {"${'$'}ref": "#/components/schemas/CreateOrderRequest"}}}}
                  }
                },
                "/internal/api/v1/orders/{orderId}": {
                  "get": {
                    "responses": {"200": {"content": {"*/*": {"schema": {"${'$'}ref": "#/components/schemas/InternalOrderResponse"}}}}}
                  }
                },
                "/internal/api/v1/cart-items/batch": {
                  "post": {
                    "requestBody": {"content": {"application/json": {"schema": {"${'$'}ref": "#/components/schemas/InternalCartItemsRequest"}}}}
                  }
                }
              },
              "components": {
                "schemas": {
                  "CreateOrderRequest": {"type": "object", "properties": {"items": {"type": "array", "items": {"${'$'}ref": "#/components/schemas/OrderItemRequest"}}}},
                  "OrderItemRequest": {"type": "object", "properties": {"productId": {"type": "integer"}}},
                  "InternalOrderResponse": {"type": "object", "properties": {"item": {"${'$'}ref": "#/components/schemas/OrderItemRequest"}, "detail": {"${'$'}ref": "#/components/schemas/InternalOrderDetail"}}},
                  "InternalOrderDetail": {"type": "object", "properties": {"reservationIds": {"type": "array", "items": {"type": "string"}}}},
                  "InternalCartItemsRequest": {"type": "object", "properties": {"cartItemIds": {"type": "array", "items": {"type": "integer"}}}}
                },
                "securitySchemes": {"bearerAuth": {"type": "http", "scheme": "bearer"}}
              }
            }
        """.trimIndent()

        private val backendPaths = CopyOnWriteArrayList<String>()

        private val backend: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { exchange ->
                backendPaths.add(exchange.requestURI.path)
                val body = API_DOCS.toByteArray()
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            start()
        }

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            val backendUrl = "http://127.0.0.1:${backend.address.port}"
            listOf("USER", "PRODUCT", "ORDER", "PAYMENT", "SETTLEMENT", "NOTIFICATION").forEach { service ->
                registry.add("ROUTES_${service}_SERVICE") { backendUrl }
            }
        }

        @JvmStatic
        @AfterAll
        fun stopBackend() {
            backend.stop(0)
        }
    }

    @Autowired
    private lateinit var webTestClient: WebTestClient

    private val objectMapper = ObjectMapper()

    @BeforeEach
    fun setUp() {
        backendPaths.clear()
    }

    private fun buildToken(subject: String, role: String): String =
        Jwts.builder()
            .setSubject(subject)
            .claim("role", role)
            .setExpiration(Date(System.currentTimeMillis() + 60_000))
            .signWith(Keys.hmacShaKeyFor(SECRET.toByteArray()), SignatureAlgorithm.HS256)
            .compact()

    @ParameterizedTest
    @ValueSource(strings = ["user-service", "product-service", "order-service", "payment-service", "settlement-service", "notification-service"])
    fun 인증_없이_서비스_API_문서를_요청하면_401이고_뒷단에_닿지_않는다(service: String) {
        webTestClient.get()
            .uri("/v3/api-docs/$service")
            .exchange()
            .expectStatus().isUnauthorized

        assertThat(backendPaths).isEmpty()
    }

    @ParameterizedTest
    @ValueSource(strings = ["BUYER", "SELLER"])
    fun ADMIN이_아닌_토큰으로_서비스_API_문서를_요청하면_403이고_뒷단에_닿지_않는다(role: String) {
        webTestClient.get()
            .uri("/v3/api-docs/order-service")
            .header("Authorization", "Bearer ${buildToken("42", role)}")
            .exchange()
            .expectStatus().isForbidden

        assertThat(backendPaths).isEmpty()
    }

    @Test
    fun ADMIN_토큰이면_문서를_받되_내부_API_경로와_내부_API만_쓰는_스키마는_빠진다() {
        val body = webTestClient.get()
            .uri("/v3/api-docs/order-service")
            .header("Authorization", "Bearer ${buildToken("1", "ADMIN")}")
            .exchange()
            .expectStatus().isOk
            .expectBody(String::class.java)
            .returnResult()
            .responseBody!!

        val docs: JsonNode = objectMapper.readTree(body)
        assertThat(backendPaths).containsExactly("/v3/api-docs")
        assertThat(docs.path("paths").fieldNames().asSequence().toList()).containsExactly("/api/v1/orders")
        assertThat(docs.path("components").path("schemas").fieldNames().asSequence().toList())
            .containsExactlyInAnyOrder("CreateOrderRequest", "OrderItemRequest")
        assertThat(docs.path("components").path("securitySchemes").has("bearerAuth")).isTrue()
        assertThat(body).doesNotContain("/internal/")
    }
}
