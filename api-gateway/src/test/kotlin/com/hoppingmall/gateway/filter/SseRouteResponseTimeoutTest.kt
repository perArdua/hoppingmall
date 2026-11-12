package com.hoppingmall.gateway.filter

import com.sun.net.httpserver.HttpServer
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import io.jsonwebtoken.security.Keys
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import java.net.InetSocketAddress
import java.time.Duration
import java.util.Date
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "jwt.secret=${SseRouteResponseTimeoutTest.SECRET}",
        "gateway.rate-limit.enabled=false",
        "spring.cloud.gateway.httpclient.response-timeout=1s"
    ]
)
@DisplayName("알림 구독(SSE) 라우트의 응답 타임아웃 제외")
@DisplayNameGeneration(ReplaceUnderscores::class)
class SseRouteResponseTimeoutTest {

    companion object {
        const val SECRET = "test-secret-key-for-testing-purposes-only-32chars!"

        private val subscribeArrived = CountDownLatch(1)
        private val releaseSubscribe = CountDownLatch(1)
        private val releaseOthers = CountDownLatch(1)

        private val backend: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            executor = Executors.newCachedThreadPool()
            createContext("/api/v1/notifications/subscribe") { exchange ->
                subscribeArrived.countDown()
                releaseSubscribe.await()
                val body = "event:connect\ndata:connected\n\n".toByteArray()
                exchange.responseHeaders.set("Content-Type", MediaType.TEXT_EVENT_STREAM_VALUE)
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            createContext("/") { exchange ->
                releaseOthers.await()
                exchange.sendResponseHeaders(200, -1)
                exchange.close()
            }
            start()
        }

        @JvmStatic
        @DynamicPropertySource
        fun routes(registry: DynamicPropertyRegistry) {
            registry.add("ROUTES_NOTIFICATION_SERVICE") { "http://127.0.0.1:${backend.address.port}" }
        }

        @JvmStatic
        @AfterAll
        fun stopBackend() {
            releaseSubscribe.countDown()
            releaseOthers.countDown()
            backend.stop(0)
        }
    }

    @LocalServerPort
    private var port: Int = 0

    private fun buildToken(subject: String, role: String): String =
        Jwts.builder()
            .setSubject(subject)
            .claim("role", role)
            .setExpiration(Date(System.currentTimeMillis() + 60_000))
            .signWith(Keys.hmacShaKeyFor(SECRET.toByteArray()), SignatureAlgorithm.HS256)
            .compact()

    private fun get(client: WebClient, path: String) =
        client.get()
            .uri(path)
            .header("Authorization", "Bearer ${buildToken("42", "BUYER")}")
            .retrieve()
            .onStatus({ true }) { Mono.empty() }
            .toEntity(String::class.java)

    @Test
    fun 응답_타임아웃이_지난_뒤에_헤더를_보내도_알림_구독은_끊기지_않고_이벤트를_받는다() {
        val client = WebClient.create("http://127.0.0.1:$port")
        val subscription = get(client, "/api/v1/notifications/subscribe").toFuture()
        assertThat(subscribeArrived.await(10, TimeUnit.SECONDS)).isTrue()

        val otherStatus = get(client, "/api/v1/notifications").block(Duration.ofSeconds(10))!!.statusCode
        releaseSubscribe.countDown()
        val subscribeResponse: ResponseEntity<String> = subscription.get(10, TimeUnit.SECONDS)

        assertThat(otherStatus).isEqualTo(HttpStatus.GATEWAY_TIMEOUT)
        assertThat(subscribeResponse.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(subscribeResponse.body).contains("data:connected")
    }
}
