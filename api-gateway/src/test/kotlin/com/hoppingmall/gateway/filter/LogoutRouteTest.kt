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
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
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
    properties = ["jwt.secret=${LogoutRouteTest.SECRET}"]
)
@DisplayName("게이트웨이를 거치는 로그아웃")
@DisplayNameGeneration(ReplaceUnderscores::class)
class LogoutRouteTest {

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
                        "authorization" to headers.getFirst("Authorization"),
                        "x-user-id" to headers.getFirst("x-user-id"),
                        "x-user-role" to headers.getFirst("x-user-role"),
                        "body" to exchange.requestBody.readAllBytes().toString(Charsets.UTF_8)
                    )
                )
                exchange.sendResponseHeaders(204, -1)
                exchange.close()
            }
            start()
        }

        @JvmStatic
        @DynamicPropertySource
        fun routes(registry: DynamicPropertyRegistry) {
            registry.add("ROUTES_USER_SERVICE") { "http://127.0.0.1:${backend.address.port}" }
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
            Mono.just(counts.merge(redisKey, 1L) { a, b -> a + b }!!)
        }
        whenever(valueOps.get(any())).thenReturn(Mono.empty())
        whenever(redisTemplate.expire(any(), any<Duration>())).thenReturn(Mono.just(true))
        whenever(redisTemplate.hasKey(any())).thenReturn(Mono.just(false))
    }

    private fun token(type: String, expiresInMillis: Long = 60_000): String =
        Jwts.builder()
            .setSubject("42")
            .claim("typ", type)
            .apply { if (type == "access") claim("role", "BUYER") }
            .setExpiration(Date(System.currentTimeMillis() + expiresInMillis))
            .signWith(Keys.hmacShaKeyFor(SECRET.toByteArray()), SignatureAlgorithm.HS256)
            .compact()

    private fun send(
        method: HttpMethod,
        path: String,
        headers: Map<String, String> = emptyMap(),
        body: String? = null
    ): Int {
        val request = webTestClient.method(method)
            .uri(path)
            .headers { httpHeaders -> headers.forEach { (name, value) -> httpHeaders.set(name, value) } }
        val spec = if (body == null) request else request.contentType(MediaType.APPLICATION_JSON).bodyValue(body)
        return spec.exchange().expectBody().returnResult().status.value()
    }

    @Test
    fun 액세스_토큰_없이_리프레시_토큰만_보내도_로그아웃이_user_service에_닿는다() {
        val body = """{"refresh_token":"${token("refresh")}"}"""

        val status = send(HttpMethod.POST, "/api/v1/auth/logout", body = body)

        assertThat(status).isEqualTo(204)
        assertThat(backendRequests).hasSize(1)
        assertThat(backendRequests[0]["path"]).isEqualTo("/api/v1/auth/logout")
        assertThat(backendRequests[0]["body"]).isEqualTo(body)
        assertThat(backendRequests[0]["authorization"]).isNull()
    }

    @Test
    fun 만료된_액세스_토큰도_검사하지_않고_Authorization_헤더를_그대로_넘긴다() {
        val expired = token("access", expiresInMillis = -60_000)

        val status = send(HttpMethod.POST, "/api/v1/auth/logout", mapOf("Authorization" to "Bearer $expired"))

        assertThat(status).isEqualTo(204)
        assertThat(backendRequests).hasSize(1)
        assertThat(backendRequests[0]["authorization"]).isEqualTo("Bearer $expired")
    }

    @Test
    fun 로그아웃에_클라이언트가_넣은_신원_헤더는_뒷단에_닿지_않는다() {
        val status = send(
            HttpMethod.POST,
            "/api/v1/auth/logout",
            mapOf("Authorization" to "Bearer ${token("access")}", "x-user-id" to "1", "x-user-role" to "ADMIN")
        )

        assertThat(status).isEqualTo(204)
        assertThat(backendRequests).hasSize(1)
        assertThat(backendRequests[0]["x-user-id"]).isNull()
        assertThat(backendRequests[0]["x-user-role"]).isNull()
    }

    @Test
    fun POST가_아닌_로그아웃과_전체_로그아웃은_지금처럼_토큰이_있어야_한다() {
        val getStatus = send(HttpMethod.GET, "/api/v1/auth/logout")
        val logoutAllStatus = send(HttpMethod.POST, "/api/v1/auth/logout-all")

        assertThat(getStatus).isEqualTo(401)
        assertThat(logoutAllStatus).isEqualTo(401)
        assertThat(backendRequests).isEmpty()
    }

    @Test
    fun 로그아웃의_요청_제한은_리프레시_토큰의_사용자로_세고_없으면_IP로_센다() {
        send(HttpMethod.POST, "/api/v1/auth/logout", body = """{"refresh_token":"${token("refresh", expiresInMillis = -60_000)}"}""")
        send(HttpMethod.POST, "/api/v1/auth/logout", mapOf("Authorization" to "Bearer ${token("access")}"))

        assertThat(incrementedKeys).hasSize(2)
        assertThat(incrementedKeys[0]).startsWith("rl:logout:user:42:")
        assertThat(incrementedKeys[1]).startsWith("rl:logout:ip:127.0.0.1:")
    }

    @Test
    fun 같은_사용자의_로그아웃은_분당_10번을_넘기면_429다() {
        val body = """{"refresh_token":"${token("refresh")}"}"""

        val statuses = (1..11).map { send(HttpMethod.POST, "/api/v1/auth/logout", body = body) }

        assertThat(statuses.take(10)).containsOnly(204)
        assertThat(statuses.last()).isEqualTo(429)
    }
}
