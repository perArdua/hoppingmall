package com.hoppingmall.cache

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import java.time.Duration

@DisplayName("HotKeyDetectorRegistry")
@DisplayNameGeneration(ReplaceUnderscores::class)
class HotKeyDetectorRegistryTest {

    private fun policy(name: String, hotKeyThreshold: Long) = CachePolicy(
        cacheName = name,
        l1MaxSize = 100,
        l1Ttl = Duration.ofSeconds(10),
        l2Ttl = Duration.ofMinutes(1),
        hotKeyThreshold = hotKeyThreshold
    )

    @Test
    fun hotKeyThreshold가_양수인_정책에_대해_Detector를_생성한다() {
        val registry = HotKeyDetectorRegistry(listOf(policy("product", 5L)))

        assertThat(registry.getDetector("product")).isNotNull()
        registry.close()
    }

    @Test
    fun hotKeyThreshold가_0인_정책은_Detector를_생성하지_않는다() {
        val registry = HotKeyDetectorRegistry(listOf(policy("product", 0L)))

        assertThat(registry.getDetector("product")).isNull()
        registry.close()
    }

    @Test
    fun 등록되지_않은_캐시명은_null을_반환한다() {
        val registry = HotKeyDetectorRegistry(listOf(policy("product", 5L)))

        assertThat(registry.getDetector("unknown")).isNull()
        registry.close()
    }

    @Test
    fun 여러_정책을_동시에_등록할_수_있다() {
        val registry = HotKeyDetectorRegistry(
            listOf(
                policy("product", 5L),
                policy("category", 10L),
                policy("user", 0L)
            )
        )

        assertThat(registry.getDetector("product")).isNotNull()
        assertThat(registry.getDetector("category")).isNotNull()
        assertThat(registry.getDetector("user")).isNull()
        registry.close()
    }

    @Test
    fun close_호출_시_모든_Detector를_종료한다() {
        val registry = HotKeyDetectorRegistry(
            listOf(
                policy("product", 5L),
                policy("category", 10L)
            )
        )

        registry.close()
    }

    @Test
    fun 빈_정책_목록으로_생성하면_모든_조회가_null을_반환한다() {
        val registry = HotKeyDetectorRegistry(emptyList())

        assertThat(registry.getDetector("any")).isNull()
        registry.close()
    }
}
