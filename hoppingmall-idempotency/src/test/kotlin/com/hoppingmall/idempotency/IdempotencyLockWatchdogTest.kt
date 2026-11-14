package com.hoppingmall.idempotency

import com.hoppingmall.common.config.RedissonDockerConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.redisson.api.RLock
import org.redisson.api.RedissonClient
import org.redisson.spring.starter.RedissonAutoConfigurationV2
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration
import org.springframework.boot.LazyInitializationBeanFactoryPostProcessor
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import redis.embedded.RedisServer
import java.net.ServerSocket
import java.util.UUID
import java.util.concurrent.TimeUnit

@DisplayName("멱등성 락 watchdog 시간")
@DisplayNameGeneration(ReplaceUnderscores::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class IdempotencyLockWatchdogTest {

    private var redisServer: RedisServer? = null
    private var up = false
    private var port = 0

    private val serviceContextRunner by lazy {
        ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RedissonAutoConfigurationV2::class.java, RedisAutoConfiguration::class.java))
            .withUserConfiguration(IdempotencyLockConfig::class.java)
            .withPropertyValues("spring.data.redis.host=127.0.0.1", "spring.data.redis.port=$port")
    }

    @BeforeAll
    fun start() {
        try {
            port = ServerSocket(0).use { it.localPort }
            redisServer = RedisServer(port).also { it.start() }
            up = true
        } catch (e: Exception) {
            up = false
        }
    }

    @AfterAll
    fun stop() {
        runCatching { redisServer?.stop() }
    }

    @Test
    fun 서비스_설정으로_만든_Redisson_클라이언트의_멱등성_락은_10초_안에_만료되도록_잡힌다() {
        assumeTrue(up)
        serviceContextRunner.run { context ->
            val lock = context.getBean(RedissonClient::class.java).getLock(lockKey())

            assertThat(lock.tryLock(0, Idempotent.LOCK_HELD_UNTIL_UNLOCK, TimeUnit.SECONDS)).isTrue()
            val remaining = lock.remainTimeToLive()
            lock.unlock()

            assertThat(remaining).isBetween(1L, WATCHDOG_TIMEOUT_MS)
        }
    }

    @Test
    fun 락을_쥔_인스턴스가_죽으면_같은_키를_12초_안에_다른_인스턴스가_잡는다() {
        assumeTrue(up)
        val key = lockKey()
        serviceContextRunner.run { survivor ->
            serviceContextRunner.run { dying ->
                val lock = dying.getBean(RedissonClient::class.java).getLock(key)
                assertThat(lock.tryLock(0, Idempotent.LOCK_HELD_UNTIL_UNLOCK, TimeUnit.SECONDS)).isTrue()
                dying.close()
            }
            val lock = survivor.getBean(RedissonClient::class.java).getLock(key)

            val acquired = tryLockUntil(lock, TimeUnit.SECONDS.toNanos(12))

            assertThat(acquired).isTrue()
            lock.unlock()
        }
    }

    @Test
    fun 락을_쥔_인스턴스가_살아_있으면_watchdog_시간이_지나도_락이_연장된다() {
        assumeTrue(up)
        serviceContextRunner.run { context ->
            val client = context.getBean(RedissonClient::class.java)
            val lock = client.getLock(lockKey())
            assertThat(lock.tryLock(0, Idempotent.LOCK_HELD_UNTIL_UNLOCK, TimeUnit.SECONDS)).isTrue()

            val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(WATCHDOG_TIMEOUT_MS + 3_000)
            while (System.nanoTime() < deadline && lock.isLocked) {
                Thread.sleep(POLL_INTERVAL_MS)
            }

            assertThat(lock.isLocked).isTrue()
            assertThat(lock.remainTimeToLive()).isPositive()
            lock.unlock()
        }
    }

    @Test
    fun docker_프로필의_Redisson_설정에도_멱등성_락_watchdog_시간이_적용된다() {
        ApplicationContextRunner()
            .withInitializer { it.addBeanFactoryPostProcessor(LazyInitializationBeanFactoryPostProcessor()) }
            .withPropertyValues("spring.profiles.active=docker")
            .withUserConfiguration(RedissonDockerConfig::class.java, IdempotencyLockConfig::class.java)
            .run { context ->
                val config = context.getBean(RedissonDockerConfig::class.java).clientConfig()

                assertThat(config.lockWatchdogTimeout).isEqualTo(WATCHDOG_TIMEOUT_MS)
                assertThat(config.isClusterConfig).isTrue()
            }
    }

    private fun tryLockUntil(lock: RLock, timeoutNanos: Long): Boolean {
        val deadline = System.nanoTime() + timeoutNanos
        while (System.nanoTime() < deadline) {
            if (lock.tryLock(0, Idempotent.LOCK_HELD_UNTIL_UNLOCK, TimeUnit.SECONDS)) return true
            Thread.sleep(POLL_INTERVAL_MS)
        }
        return false
    }

    private fun lockKey(): String = "idempotency:${UUID.randomUUID()}"

    companion object {
        private const val WATCHDOG_TIMEOUT_MS = 10_000L
        private const val POLL_INTERVAL_MS = 200L
    }
}
