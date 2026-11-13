package com.hoppingmall.gateway.filter

import com.sun.net.httpserver.HttpServer
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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "jwt.secret=test-secret-key-for-testing-purposes-only-32chars!",
        "gateway.rate-limit.trusted-proxy-count=1"
    ]
)
@DisplayName("믿는 프록시 뒤의 레이트리밋 IP 키")
@DisplayNameGeneration(ReplaceUnderscores::class)
class RateLimitTrustedProxyTest {

    companion object {
        private val backend: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { exchange ->
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
        val valueOps: ReactiveValueOperations<String, String> = mock()
        whenever(redisTemplate.opsForValue()).thenReturn(valueOps)
        whenever(valueOps.increment(any<String>())).thenAnswer { invocation ->
            val redisKey = invocation.getArgument<String>(0)
            incrementedKeys.add(redisKey)
            Mono.just(counts.merge(redisKey.substringBeforeLast(':'), 1L) { a, b -> a + b }!!)
        }
        whenever(redisTemplate.expire(any(), any<Duration>())).thenReturn(Mono.just(true))
    }

    private fun post(path: String, forwardedFor: String): Int =
        webTestClient.post()
            .uri(path)
            .header("X-Forwarded-For", forwardedFor)
            .exchange()
            .expectBody()
            .returnResult()
            .status
            .value()

    private fun limitKeys(): List<String> = incrementedKeys.map { it.substringBeforeLast(':') }.distinct()

    @Test
    fun 믿는_프록시가_하나면_프록시가_덧붙인_주소가_로그인_한도의_키가_된다() {
        val status = post("/api/v1/users/login", "203.0.113.7")

        assertThat(status).isEqualTo(200)
        assertThat(limitKeys()).containsExactly("rl:login:203.0.113.7")
    }

    @Test
    fun 클라이언트가_앞에_끼워_넣은_가짜_주소를_바꿔도_같은_클라이언트는_로그인_한도_5회에_걸린다() {
        val statuses = (1..6).map { i -> post("/api/v1/users/login", "198.51.100.$i, 203.0.113.7") }

        assertThat(statuses).containsExactly(200, 200, 200, 200, 200, 429)
        assertThat(limitKeys()).containsExactly("rl:login:203.0.113.7")
    }

    @Test
    fun 같은_프록시를_거쳐도_다른_클라이언트는_로그인_한도를_따로_가진다() {
        val firstClientStatuses = (1..5).map { post("/api/v1/users/login", "203.0.113.7") }
        val secondClientStatus = post("/api/v1/users/login", "203.0.113.8")

        assertThat(firstClientStatuses).containsOnly(200)
        assertThat(secondClientStatus).isEqualTo(200)
        assertThat(limitKeys()).containsExactly("rl:login:203.0.113.7", "rl:login:203.0.113.8")
    }

    @Test
    fun 인증_없는_재발급_요청의_사용자_한도도_프록시가_덧붙인_주소로_묶인다() {
        val status = post("/api/v1/auth/refresh", "198.51.100.1, 203.0.113.7")

        assertThat(status).isEqualTo(200)
        assertThat(limitKeys()).containsExactly("rl:refresh:203.0.113.7")
    }
}
