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
    properties = ["jwt.secret=${RefreshRateLimitKeyTest.SECRET}"]
)
@DisplayName("재발급 입구의 요청 제한 키")
@DisplayNameGeneration(ReplaceUnderscores::class)
class RefreshRateLimitKeyTest {

    companion object {
        const val SECRET = "test-secret-key-for-testing-purposes-only-32chars!"
        private const val OTHER_SECRET = "another-secret-key-for-forged-tokens-only-32ch!"

        private val backendBodies = CopyOnWriteArrayList<String>()

        private val backend: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { exchange ->
                backendBodies.add(exchange.requestBody.readAllBytes().toString(Charsets.UTF_8))
                exchange.sendResponseHeaders(200, -1)
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
        backendBodies.clear()
        val valueOps: ReactiveValueOperations<String, String> = mock()
        whenever(redisTemplate.opsForValue()).thenReturn(valueOps)
        whenever(valueOps.increment(any<String>())).thenAnswer { invocation ->
            val redisKey = invocation.getArgument<String>(0)
            incrementedKeys.add(redisKey)
            Mono.just(counts.merge(redisKey, 1L) { a, b -> a + b }!!)
        }
        whenever(redisTemplate.expire(any(), any<Duration>())).thenReturn(Mono.just(true))
    }

    private fun refreshToken(
        subject: String,
        type: String = "refresh",
        secret: String = SECRET,
        expiresInMillis: Long = 60_000
    ): String =
        Jwts.builder()
            .setSubject(subject)
            .claim("typ", type)
            .setExpiration(Date(System.currentTimeMillis() + expiresInMillis))
            .signWith(Keys.hmacShaKeyFor(secret.toByteArray()), SignatureAlgorithm.HS256)
            .compact()

    private fun refresh(body: String?): Int {
        val request = webTestClient.post().uri("/api/v1/auth/refresh")
        val spec = if (body == null) request else request.contentType(MediaType.APPLICATION_JSON).bodyValue(body)
        return spec.exchange().expectBody().returnResult().status.value()
    }

    private fun bodyWith(token: String) = """{"refresh_token":"$token"}"""

    @Test
    fun 같은_IP에서도_사용자마다_재발급_한도를_따로_가진다() {
        val firstUser = (1..11).map { refresh(bodyWith(refreshToken("7"))) }
        val secondUser = refresh(bodyWith(refreshToken("42")))

        assertThat(firstUser.take(10)).containsOnly(200)
        assertThat(firstUser.last()).isEqualTo(429)
        assertThat(secondUser).isEqualTo(200)
    }

    @Test
    fun 서명이_맞는_리프레시_토큰이면_그_사용자로_센다() {
        refresh(bodyWith(refreshToken("42")))

        assertThat(incrementedKeys).hasSize(1)
        assertThat(incrementedKeys[0]).startsWith("rl:refresh:user:42:")
    }

    @Test
    fun 만료됐어도_서명이_맞는_리프레시_토큰이면_그_사용자로_센다() {
        refresh(bodyWith(refreshToken("42", expiresInMillis = -60_000)))

        assertThat(incrementedKeys).hasSize(1)
        assertThat(incrementedKeys[0]).startsWith("rl:refresh:user:42:")
    }

    @Test
    fun 토큰이_없거나_위조됐거나_액세스_토큰이면_클라이언트_IP로_센다() {
        refresh(null)
        refresh("""{}""")
        refresh("not-json")
        refresh(bodyWith("not-a-jwt"))
        refresh(bodyWith(refreshToken("42", secret = OTHER_SECRET)))
        refresh(bodyWith(refreshToken("42", type = "access")))

        assertThat(incrementedKeys).hasSize(6)
        assertThat(incrementedKeys).allSatisfy { assertThat(it).startsWith("rl:refresh:ip:127.0.0.1:") }
    }

    @Test
    fun 키를_정하려고_읽은_본문은_뒷단에_그대로_넘어간다() {
        val body = bodyWith(refreshToken("42"))

        val status = refresh(body)

        assertThat(status).isEqualTo(200)
        assertThat(backendBodies).containsExactly(body)
    }

    @Test
    fun 본문이_8KB를_넘으면_413이고_뒷단에_닿지_않는다() {
        val status = refresh(bodyWith("a".repeat(9_000)))

        assertThat(status).isEqualTo(413)
        assertThat(backendBodies).isEmpty()
    }
}
