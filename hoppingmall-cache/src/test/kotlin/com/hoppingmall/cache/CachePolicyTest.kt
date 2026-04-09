package com.hoppingmall.cache

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import java.time.Duration

@DisplayName("CachePolicy")
@DisplayNameGeneration(ReplaceUnderscores::class)
class CachePolicyTest {

    @Test
    fun hotKeyThreshold가_양수이면_dynamicHotKeyEnabled가_true이다() {
        val policy = CachePolicy(
            cacheName = "product",
            l1MaxSize = 100,
            l1Ttl = Duration.ofSeconds(10),
            l2Ttl = Duration.ofMinutes(1),
            hotKeyThreshold = 1L
        )

        assertThat(policy.dynamicHotKeyEnabled).isTrue()
    }

    @Test
    fun hotKeyThreshold가_0이면_dynamicHotKeyEnabled가_false이다() {
        val policy = CachePolicy(
            cacheName = "product",
            l1MaxSize = 100,
            l1Ttl = Duration.ofSeconds(10),
            l2Ttl = Duration.ofMinutes(1),
            hotKeyThreshold = 0L
        )

        assertThat(policy.dynamicHotKeyEnabled).isFalse()
    }

    @Test
    fun 기본값이_올바르게_설정된다() {
        val policy = CachePolicy(
            cacheName = "product",
            l1MaxSize = 200,
            l1Ttl = Duration.ofSeconds(30),
            l2Ttl = Duration.ofMinutes(5)
        )

        assertThat(policy.jitterPercent).isEqualTo(10)
        assertThat(policy.hotKeyThreshold).isEqualTo(0L)
        assertThat(policy.hotKeyWindow).isEqualTo(Duration.ofSeconds(60))
        assertThat(policy.hotKeyShardCount).isEqualTo(4)
    }

    @Test
    fun 커스텀_값으로_생성하면_해당_값이_유지된다() {
        val policy = CachePolicy(
            cacheName = "order",
            l1MaxSize = 500,
            l1Ttl = Duration.ofSeconds(60),
            l2Ttl = Duration.ofMinutes(10),
            jitterPercent = 20,
            hotKeyThreshold = 100L,
            hotKeyWindow = Duration.ofSeconds(30),
            hotKeyShardCount = 8
        )

        assertThat(policy.cacheName).isEqualTo("order")
        assertThat(policy.l1MaxSize).isEqualTo(500L)
        assertThat(policy.jitterPercent).isEqualTo(20)
        assertThat(policy.hotKeyThreshold).isEqualTo(100L)
        assertThat(policy.hotKeyWindow).isEqualTo(Duration.ofSeconds(30))
        assertThat(policy.hotKeyShardCount).isEqualTo(8)
    }
}
