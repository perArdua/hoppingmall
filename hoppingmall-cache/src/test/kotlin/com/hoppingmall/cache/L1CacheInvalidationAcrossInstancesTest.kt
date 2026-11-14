package com.hoppingmall.cache

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.redisson.Redisson
import org.redisson.api.RedissonClient
import org.redisson.config.Config
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.context.annotation.ImportCandidates
import org.springframework.boot.test.context.assertj.AssertableApplicationContext
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.cache.Cache
import org.springframework.cache.CacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import redis.embedded.RedisServer
import java.time.Duration
import java.util.concurrent.Callable
import java.util.concurrent.atomic.AtomicReference

@DisplayName("인스턴스 사이 L1 캐시 무효화")
@DisplayNameGeneration(ReplaceUnderscores::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class L1CacheInvalidationAcrossInstancesTest {

    private var port = 0
    private var redisServer: RedisServer? = null
    private var up = false
    private val source = AtomicReference("v1")
    private val loader = Callable { source.get() }

    @BeforeAll
    fun start() {
        port = java.net.ServerSocket(0).use { it.localPort }
        up = runCatching { startRedis() }.isSuccess
    }

    @AfterAll
    fun stop() {
        runCatching { redisServer?.stop() }
    }

    @BeforeEach
    fun reset() {
        assumeTrue(up, "embedded redis 미기동")
        source.set("v1")
    }

    @Test
    fun 한_인스턴스에서_키를_무효화하면_다른_인스턴스_L1도_바로_비워진다() {
        runTwoInstances { a, b ->
            assertThat(a.get(11L, loader)).isEqualTo("v1")
            assertThat(b.get(11L, loader)).isEqualTo("v1")

            source.set("v2")
            a.evict(11L)

            assertThat(eventually { b.get(11L, loader) == "v2" }).isTrue()
        }
    }

    @Test
    fun 한_인스턴스에서_캐시를_비우면_다른_인스턴스_L1도_바로_비워진다() {
        runTwoInstances { a, b ->
            assertThat(b.get(21L, loader)).isEqualTo("v1")
            assertThat(b.get(22L, loader)).isEqualTo("v1")

            source.set("v2")
            a.clear()

            assertThat(eventually { b.get(21L, loader) == "v2" && b.get(22L, loader) == "v2" }).isTrue()
        }
    }

    @Test
    fun 문자열_키도_다른_인스턴스_L1에서_비워진다() {
        runTwoInstances { a, b ->
            assertThat(b.get("all", loader)).isEqualTo("v1")

            source.set("v2")
            a.evict("all")

            assertThat(eventually { b.get("all", loader) == "v2" }).isTrue()
        }
    }

    @Test
    fun Redis가_끊겼다_다시_붙으면_놓친_무효화가_있을_수_있어_L1을_통째로_비운다() {
        runTwoInstances { _, b ->
            assertThat(b.get(31L, loader)).isEqualTo("v1")

            redisServer?.stop()
            source.set("v2")
            restartRedis()

            assertThat(eventually(Duration.ofSeconds(40)) { b.get(31L, loader) == "v2" }).isTrue()
        }
    }

    private fun runTwoInstances(block: (Cache, Cache) -> Unit) {
        val runner = ApplicationContextRunner()
            .withUserConfiguration(InstanceConfig::class.java)
            .withConfiguration(AutoConfigurations.of(*cacheModuleAutoConfigurations()))
            .withPropertyValues("test.redis.port=$port", "spring.application.name=l1-invalidation-test")
        runner.run { contextA ->
            runner.run { contextB ->
                block(productCache(contextA), productCache(contextB))
            }
        }
    }

    private fun productCache(context: AssertableApplicationContext): Cache {
        assertThat(context).hasNotFailed()
        return context.getBean(CacheManager::class.java).getCache("product")!!
    }

    private fun cacheModuleAutoConfigurations(): Array<Class<*>> =
        ImportCandidates.load(AutoConfiguration::class.java, javaClass.classLoader)
            .filter { it.startsWith("com.hoppingmall.cache") }
            .map { Class.forName(it) }
            .toTypedArray()

    private fun eventually(timeout: Duration = Duration.ofSeconds(3), condition: () -> Boolean): Boolean {
        val deadline = System.nanoTime() + timeout.toNanos()
        while (System.nanoTime() < deadline) {
            if (runCatching(condition).getOrDefault(false)) return true
            Thread.sleep(50)
        }
        return runCatching(condition).getOrDefault(false)
    }

    private fun startRedis() {
        redisServer = RedisServer(port).also { it.start() }
    }

    private fun restartRedis() {
        repeat(20) {
            if (runCatching { startRedis() }.isSuccess) return
            Thread.sleep(250)
        }
        startRedis()
    }

    @Configuration(proxyBeanMethods = false)
    class InstanceConfig {

        private val policies = mapOf(
            "product" to CachePolicy(
                cacheName = "product",
                l1MaxSize = 100,
                l1Ttl = Duration.ofMinutes(10),
                l2Ttl = Duration.ofMinutes(30)
            )
        )

        @Bean(destroyMethod = "shutdown")
        fun redissonClient(@Value("\${test.redis.port}") port: Int): RedissonClient =
            Redisson.create(Config().apply { useSingleServer().address = "redis://127.0.0.1:$port" })

        @Bean
        fun redisConnectionFactory(@Value("\${test.redis.port}") port: Int): LettuceConnectionFactory =
            LettuceConnectionFactory(RedisStandaloneConfiguration("127.0.0.1", port))

        @Bean
        fun cacheManager(redisConnectionFactory: RedisConnectionFactory): CacheManager =
            TwoLevelCacheManager(
                redisCacheManager = TwoLevelCacheManager.buildRedisCacheManager(redisConnectionFactory, policies),
                policies = policies,
                hotKeyDetectorRegistry = HotKeyDetectorRegistry(policies.values)
            )
    }
}
