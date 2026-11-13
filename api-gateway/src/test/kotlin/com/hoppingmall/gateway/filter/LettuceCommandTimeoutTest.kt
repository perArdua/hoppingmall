package com.hoppingmall.gateway.filter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeoutException
import kotlin.concurrent.thread

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["jwt.secret=${LettuceCommandTimeoutTest.SECRET}"]
)
@DisplayName("Redis가 응답하지 않을 때 Lettuce가 명령을 붙잡는 시간")
@DisplayNameGeneration(ReplaceUnderscores::class)
class LettuceCommandTimeoutTest {

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

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.data.redis.host") { "127.0.0.1" }
            registry.add("spring.data.redis.port") { silentRedis.localPort }
        }

        @JvmStatic
        @AfterAll
        fun stopServer() {
            heldSockets.forEach { runCatching { it.close() } }
            silentRedis.close()
        }
    }

    @Autowired
    private lateinit var connectionFactory: LettuceConnectionFactory

    @Autowired
    private lateinit var redisTemplate: ReactiveStringRedisTemplate

    @Test
    fun 운영_설정이_없으면_Lettuce_명령_시간_제한은_1초다() {
        assertThat(connectionFactory.clientConfiguration.commandTimeout).isEqualTo(Duration.ofSeconds(1))
    }

    @Test
    fun Redis가_응답하지_않으면_Lettuce가_요청을_버린_명령을_수_초_안에_실패로_끝낸다() {
        val startedAt = System.nanoTime()

        val failure = runCatching {
            redisTemplate.hasKey("gateway:lettuce-timeout-probe")
                .timeout(Duration.ofSeconds(10))
                .block()
        }.exceptionOrNull()

        val elapsed = Duration.ofNanos(System.nanoTime() - startedAt)
        assertThat(failure).isNotNull()
        assertThat(generateSequence(failure) { it.cause }.toList()).noneMatch { it is TimeoutException }
        assertThat(elapsed).isLessThan(Duration.ofSeconds(5))
    }
}
