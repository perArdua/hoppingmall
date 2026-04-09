package com.hoppingmall.cache

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import java.time.Duration

@DisplayName("TtlJitter 엣지케이스")
@DisplayNameGeneration(ReplaceUnderscores::class)
class TtlJitterEdgeCaseTest {

    @Test
    fun 음수_jitterPercent는_base_TTL을_그대로_반환한다() {
        val baseTtl = Duration.ofSeconds(300)

        val result = TtlJitter.apply(baseTtl, -5)

        assertThat(result.seconds).isEqualTo(300)
    }

    @Test
    fun jitter_100퍼센트면_base의_두배까지_범위다() {
        val baseTtl = Duration.ofSeconds(100)

        repeat(50) {
            val result = TtlJitter.apply(baseTtl, 100)
            assertThat(result.seconds).isBetween(100L, 200L)
        }
    }

    @Test
    fun base가_1초일때_jitter가_적용된다() {
        val baseTtl = Duration.ofSeconds(1)

        repeat(20) {
            val result = TtlJitter.apply(baseTtl, 100)
            assertThat(result.seconds).isBetween(1L, 2L)
        }
    }
}
