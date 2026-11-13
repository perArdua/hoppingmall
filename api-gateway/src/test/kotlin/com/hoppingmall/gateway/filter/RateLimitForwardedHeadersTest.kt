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
        "spring.main.cloud-platform=kubernetes"
    ]
)
@DisplayName("쿠버네티스에서 전달 헤더가 레이트리밋 IP 키를 바꾸지 못함")
@DisplayNameGeneration(ReplaceUnderscores::class)
class RateLimitForwardedHeadersTest {

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

    private fun login(forwardedFor: String): Int =
        webTestClient.post()
            .uri("/api/v1/users/login")
            .header("X-Forwarded-For", forwardedFor)
            .exchange()
            .expectBody()
            .returnResult()
            .status
            .value()

    private fun limitKeys(): List<String> = incrementedKeys.map { it.substringBeforeLast(':') }.distinct()

    @Test
    fun 믿는_프록시가_없으면_클라이언트가_보낸_X_Forwarded_For가_아니라_연결_주소가_로그인_한도의_키다() {
        val status = login("203.0.113.7")

        assertThat(status).isEqualTo(200)
        assertThat(limitKeys()).containsExactly("rl:login:127.0.0.1")
    }

    @Test
    fun X_Forwarded_For만_바꿔_로그인을_6번_요청하면_6번째는_429다() {
        val statuses = (1..6).map { i -> login("198.51.100.$i") }

        assertThat(statuses).containsExactly(200, 200, 200, 200, 200, 429)
        assertThat(limitKeys()).containsExactly("rl:login:127.0.0.1")
    }
}
