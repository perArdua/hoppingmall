package com.hoppingmall.gateway.filter

import com.sun.net.httpserver.HttpServer
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import io.jsonwebtoken.security.Keys
import io.micrometer.core.instrument.MeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.time.Duration
import java.util.Date
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["jwt.secret=${RedisUnresponsiveTest.SECRET}"]
)
@DisplayName("Redis가 연결은 받고 응답하지 않을 때의 게이트웨이 응답")
@DisplayNameGeneration(ReplaceUnderscores::class)
@ExtendWith(OutputCaptureExtension::class)
class RedisUnresponsiveTest {

    companion object {
        const val SECRET = "test-secret-key-for-testing-purposes-only-32chars!"

        private val heldSockets = CopyOnWriteArrayList<Socket>()

        private val silentRedis: ServerSocket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1")).also { server ->
            thread(isDaemon = true) {
                while (!server.isClosed) {
                    val socket = runCatching { server.accept() }.getOrNull() ?: break
                    heldSockets.add(socket)
                    thread(isDaemon = true) {
                        runCatching {
                            val input = socket.getInputStream()
                            val buffer = ByteArray(1024)
                            while (input.read(buffer) >= 0) {
                                continue
                            }
                        }
                    }
                }
            }
        }

        private val backendPaths = CopyOnWriteArrayList<String>()

        private val backend: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { exchange ->
                backendPaths.add(exchange.requestURI.path)
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
            registry.add("spring.data.redis.host") { "127.0.0.1" }
            registry.add("spring.data.redis.port") { silentRedis.localPort }
        }

        @JvmStatic
        @AfterAll
        fun stopServers() {
            backend.stop(0)
            heldSockets.forEach { runCatching { it.close() } }
            silentRedis.close()
        }
    }

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @Autowired
    private lateinit var meterRegistry: MeterRegistry

    @BeforeEach
    fun setUp() {
        backendPaths.clear()
    }

    private fun accessToken(): String =
        Jwts.builder()
            .setSubject("42")
            .claim("typ", "access")
            .claim("role", "BUYER")
            .setIssuedAt(Date())
            .setExpiration(Date(System.currentTimeMillis() + 60_000))
            .signWith(Keys.hmacShaKeyFor(SECRET.toByteArray()), SignatureAlgorithm.HS256)
            .compact()

    private fun count(metric: String): Double =
        meterRegistry.find(metric).counters().sumOf { it.count() }

    @Test
    fun Redis가_응답하지_않으면_제한_시간_뒤_주문_생성_요청이_뒷단까지_가고_실패_지표와_WARN_로그를_남긴다(output: CapturedOutput) {
        val client = webTestClient.mutate().responseTimeout(Duration.ofSeconds(10)).build()
        val rateLimitFailuresBefore = count("gateway.ratelimit.redis.failures")
        val blacklistFailuresBefore = count("gateway.jwt.blacklist.redis.failures")
        val startedAt = System.nanoTime()

        client.post()
            .uri("/api/v1/orders")
            .header("Authorization", "Bearer ${accessToken()}")
            .exchange()
            .expectStatus().isOk

        val elapsed = Duration.ofNanos(System.nanoTime() - startedAt)
        assertThat(backendPaths).containsExactly("/api/v1/orders")
        assertThat(elapsed).isLessThan(Duration.ofSeconds(5))
        assertThat(count("gateway.ratelimit.redis.failures")).isEqualTo(rateLimitFailuresBefore + 1)
        assertThat(count("gateway.jwt.blacklist.redis.failures")).isEqualTo(blacklistFailuresBefore + 1)
        assertThat(output.out.lines()).anySatisfy { line ->
            assertThat(line).contains("WARN").contains("RateLimitGlobalFilter").contains("policy=create-order")
        }
        assertThat(output.out.lines()).anySatisfy { line ->
            assertThat(line).contains("WARN").contains("JwtValidationGatewayFilterFactory")
        }
    }

    @Test
    fun Redis가_응답하지_않아도_인증_없는_로그인_요청은_제한_시간_뒤_뒷단까지_간다() {
        val client = webTestClient.mutate().responseTimeout(Duration.ofSeconds(10)).build()
        val startedAt = System.nanoTime()

        client.post()
            .uri("/api/v1/users/login")
            .exchange()
            .expectStatus().isOk

        val elapsed = Duration.ofNanos(System.nanoTime() - startedAt)
        assertThat(backendPaths).containsExactly("/api/v1/users/login")
        assertThat(elapsed).isLessThan(Duration.ofSeconds(5))
    }
}
