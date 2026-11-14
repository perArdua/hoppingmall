package com.hoppingmall.cache

import com.hoppingmall.cache.config.L1CacheInvalidationAutoConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.redisson.api.RTopic
import org.redisson.api.RedissonClient
import org.redisson.client.codec.Codec
import org.redisson.client.codec.StringCodec
import org.redisson.misc.CompletableFutureWrapper
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.cache.CacheManager
import org.springframework.cache.concurrent.ConcurrentMapCache
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import java.time.Duration
import java.util.stream.Stream

@DisplayName("L1CacheInvalidationRegistrar")
@DisplayNameGeneration(ReplaceUnderscores::class)
class L1CacheInvalidationRegistrarTest {

    private val policies = mapOf(
        "product" to CachePolicy("product", 10, Duration.ofMinutes(10), Duration.ofMinutes(30))
    )
    private val redissonClient: RedissonClient = mock()
    private val topic: RTopic = mock()

    private fun twoLevelCacheManager(): TwoLevelCacheManager {
        val redisCacheManager: CacheManager = mock()
        whenever(redisCacheManager.getCache("product")).thenReturn(ConcurrentMapCache("product"))
        return TwoLevelCacheManager(redisCacheManager, policies, HotKeyDetectorRegistry(policies.values))
    }

    private fun <T : Any> provider(vararg beans: T): ObjectProvider<T> {
        val provider: ObjectProvider<T> = mock()
        whenever(provider.orderedStream()).thenAnswer { Stream.of(*beans) }
        whenever(provider.ifUnique).thenReturn(beans.singleOrNull())
        return provider
    }

    @Test
    fun 두_단계_캐시_매니저가_있으면_무효화를_발행하게_붙이고_닫으면_뗀다() {
        whenever(redissonClient.getTopic(eq("topic"), eq(StringCodec.INSTANCE))).thenReturn(topic)
        whenever(topic.publishAsync(any())).thenReturn(CompletableFutureWrapper(1L))
        val manager = twoLevelCacheManager()
        val registrar = L1CacheInvalidationRegistrar(
            provider<CacheManager>(ConcurrentMapCacheManager(), manager), provider(redissonClient), "topic"
        )

        registrar.afterSingletonsInstantiated()
        manager.getCache("product")!!.evict(1L)
        verify(topic).publishAsync(any())

        registrar.destroy()
        manager.getCache("product")!!.evict(2L)
        verify(topic).publishAsync(any())
    }

    @Test
    fun 두_단계_캐시_매니저가_없으면_Redis를_찾지_않는다() {
        val redisson = provider(redissonClient)
        val registrar = L1CacheInvalidationRegistrar(provider<CacheManager>(ConcurrentMapCacheManager()), redisson, "topic")

        registrar.afterSingletonsInstantiated()

        verify(redisson, never()).ifUnique
    }

    @Test
    fun RedissonClient가_없으면_붙이지_않는다() {
        val manager = twoLevelCacheManager()
        val registrar = L1CacheInvalidationRegistrar(provider<CacheManager>(manager), provider<RedissonClient>(), "topic")

        registrar.afterSingletonsInstantiated()
        manager.getCache("product")!!.evict(1L)

        verify(redissonClient, never()).getTopic(any<String>(), any<Codec>())
    }

    @Test
    fun 자동_구성은_기본으로_켜지고_속성으로_끌_수_있다() {
        val runner = ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(L1CacheInvalidationAutoConfiguration::class.java))

        runner.run { assertThat(it).hasSingleBean(L1CacheInvalidationRegistrar::class.java) }
        runner.withPropertyValues("hoppingmall.cache.l1-invalidation.enabled=false")
            .run { assertThat(it).doesNotHaveBean(L1CacheInvalidationRegistrar::class.java) }
    }
}
