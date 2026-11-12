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
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.reactive.server.WebTestClient
import reactor.core.publisher.Mono
import java.net.InetSocketAddress
import java.time.Duration
import java.util.Date
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["jwt.secret=${RateLimitIdentityKeyTest.SECRET}"]
)
@DisplayName("레이트리밋 사용자 키와 클라이언트 신원 헤더")
@DisplayNameGeneration(ReplaceUnderscores::class)
class RateLimitIdentityKeyTest {

    companion object {
        const val SECRET = "test-secret-key-for-testing-purposes-only-32chars!"

        private val backendRequests = CopyOnWriteArrayList<Map<String, String?>>()

        private val backend: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { exchange ->
                val headers = exchange.requestHeaders
                backendRequests.add(
                    mapOf(
                        "path" to exchange.requestURI.path,
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

    private val counts = ConcurrentHashMap<String, Long>()
    private val incrementedKeys = CopyOnWriteArrayList<String>()

    @BeforeEach
    fun setUp() {
        backendRequests.clear()
        val valueOps: ReactiveValueOperations<String, String> = mock()
        whenever(redisTemplate.opsForValue()).thenReturn(valueOps)
        whenever(valueOps.increment(any<String>())).thenAnswer { invocation ->
            val redisKey = invocation.getArgument<String>(0)
            incrementedKeys.add(redisKey)
            Mono.just(counts.merge(redisKey.substringBeforeLast(':'), 1L) { a, b -> a + b }!!)
        }
        whenever(redisTemplate.expire(any(), any<Duration>())).thenReturn(Mono.just(true))
    }

    private fun buildToken(subject: String, role: String): String =
        Jwts.builder()
            .setSubject(subject)
            .claim("role", role)
            .setExpiration(Date(System.currentTimeMillis() + 60_000))
            .signWith(Keys.hmacShaKeyFor(SECRET.toByteArray()), SignatureAlgorithm.HS256)
            .compact()

    private fun post(path: String, headers: Map<String, String>): Int =
        webTestClient.post()
            .uri(path)
            .headers { httpHeaders -> headers.forEach { (name, value) -> httpHeaders.set(name, value) } }
            .exchange()
            .expectBody()
            .returnResult()
            .status
            .value()

    @Test
    fun 같은_토큰이면_x_user_id_헤더를_요청마다_바꿔도_주문_생성_한도_5회에_걸린다() {
        val token = buildToken("42", "BUYER")

        val statuses = (1..6).map { i ->
            post("/api/v1/orders", mapOf("Authorization" to "Bearer $token", "x-user-id" to "spoofed-$i"))
        }

        assertThat(statuses).containsExactly(200, 200, 200, 200, 200, 429)
        assertThat(incrementedKeys).allSatisfy { assertThat(it).startsWith("rl:create-order:42:") }
    }

    @Test
    fun 같은_IP에서_보내도_서로_다른_사용자는_주문_생성_한도를_따로_가진다() {
        val firstUserToken = buildToken("7", "BUYER")
        val secondUserToken = buildToken("42", "BUYER")

        val firstUserStatuses = (1..5).map { post("/api/v1/orders", mapOf("Authorization" to "Bearer $firstUserToken")) }
        val secondUserStatus = post("/api/v1/orders", mapOf("Authorization" to "Bearer $secondUserToken"))

        assertThat(firstUserStatuses).containsOnly(200)
        assertThat(secondUserStatus).isEqualTo(200)
    }

    @Test
    fun 인증_없는_라우트로_보낸_x_user_role과_x_user_id는_뒷단_요청에_없다() {
        val status = post("/api/v1/users/login", mapOf("x-user-role" to "ADMIN", "x-user-id" to "1"))

        assertThat(status).isEqualTo(200)
        assertThat(backendRequests).hasSize(1)
        assertThat(backendRequests[0]["path"]).isEqualTo("/api/v1/users/login")
        assertThat(backendRequests[0]["x-user-role"]).isNull()
        assertThat(backendRequests[0]["x-user-id"]).isNull()
    }

    @Test
    fun 인증_없는_라우트의_사용자_한도는_x_user_id를_바꿔도_IP로_묶인다() {
        val statuses = (1..11).map { i -> post("/api/v1/auth/refresh", mapOf("x-user-id" to "spoofed-$i")) }

        assertThat(statuses.take(10)).containsOnly(200)
        assertThat(statuses.last()).isEqualTo(429)
        assertThat(incrementedKeys.map { it.substringBeforeLast(':') }.distinct()).hasSize(1)
        assertThat(incrementedKeys).noneMatch { it.contains("spoofed") }
    }

    @Test
    fun JWT_라우트는_클라이언트가_보낸_신원_헤더_대신_토큰의_사용자와_역할을_뒷단에_넘긴다() {
        val token = buildToken("42", "BUYER")

        val status = post(
            "/api/v1/orders",
            mapOf("Authorization" to "Bearer $token", "x-user-id" to "999", "x-user-role" to "ADMIN")
        )

        assertThat(status).isEqualTo(200)
        assertThat(backendRequests).hasSize(1)
        assertThat(backendRequests[0]["x-user-id"]).isEqualTo("42")
        assertThat(backendRequests[0]["x-user-role"]).isEqualTo("BUYER")
    }
}
