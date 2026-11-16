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
import java.util.concurrent.CopyOnWriteArrayList

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["jwt.secret=test-secret-key-for-testing-purposes-only-32chars!"]
)
@DisplayName("게이트웨이가 user-service로 넘기는 X-Forwarded-For")
@DisplayNameGeneration(ReplaceUnderscores::class)
class ForwardedForToBackendTest {

    companion object {
        private val forwardedFor = CopyOnWriteArrayList<List<String>>()

        private val backend: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { exchange ->
                forwardedFor.add(
                    exchange.requestHeaders["X-Forwarded-For"].orEmpty()
                        .flatMap { it.split(",") }
                        .map { it.trim() }
                )
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

    @BeforeEach
    fun setUp() {
        forwardedFor.clear()
        val valueOps: ReactiveValueOperations<String, String> = mock()
        whenever(redisTemplate.opsForValue()).thenReturn(valueOps)
        whenever(valueOps.increment(any<String>())).thenReturn(Mono.just(1L))
        whenever(redisTemplate.expire(any(), any<Duration>())).thenReturn(Mono.just(true))
    }

    @Test
    fun 클라이언트가_보낸_주소_뒤에_게이트웨이가_본_연결_주소를_한_단_덧붙인다() {
        webTestClient.post().uri("/api/v1/users/login")
            .header("X-Forwarded-For", "203.0.113.9")
            .exchange()
            .expectStatus().isOk

        val received = forwardedFor.single()
        assertThat(received).hasSize(2)
        assertThat(received.first()).isEqualTo("203.0.113.9")
        assertThat(received.last()).isNotEqualTo("203.0.113.9")
    }

    @Test
    fun 클라이언트가_헤더를_보내지_않아도_게이트웨이가_본_연결_주소_하나가_넘어간다() {
        webTestClient.post().uri("/api/v1/users/login")
            .exchange()
            .expectStatus().isOk

        assertThat(forwardedFor.single()).hasSize(1)
    }
}
