package com.hoppingmall.notification.config

import com.hoppingmall.cache.TwoLevelCacheManager
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.redisson.api.RedissonClient
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.cache.CacheManager
import org.springframework.data.redis.connection.RedisConnectionFactory

@DisplayName("CacheConfig")
@DisplayNameGeneration(ReplaceUnderscores::class)
class CacheConfigTest {

    private val contextRunner = ApplicationContextRunner()
        .withUserConfiguration(CacheConfig::class.java)
        .withBean(RedisConnectionFactory::class.java, { mock<RedisConnectionFactory>() })
        .withBean(RedissonClient::class.java, { mock<RedissonClient>() })
        .withBean(MeterRegistry::class.java, { SimpleMeterRegistry() })

    @Test
    fun 캐시_설정이_로드되면_단일_CacheManager로_TwoLevelCacheManager가_선택된다() {
        contextRunner.run { context ->
            assertThat(context).hasNotFailed()
            assertThat(context.getBean(CacheManager::class.java)).isInstanceOf(TwoLevelCacheManager::class.java)
        }
    }
}
