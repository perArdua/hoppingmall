package com.hoppingmall.cache

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import java.time.Duration

@DisplayName("TtlJitter")
@DisplayNameGeneration(ReplaceUnderscores::class)
class TtlJitterTest {

    @Test
    fun jitter_적용_시_base_이상_base_플러스_jitter_이하_범위의_TTL을_반환한다() {
        val baseTtl = Duration.ofSeconds(600)
        val jitterPercent = 10

        repeat(100) {
            val result = TtlJitter.apply(baseTtl, jitterPercent)
            assertTrue(result.seconds in 600..660,
                "TTL ${result.seconds}s 가 범위 [600, 660] 밖입니다")
        }
    }

    @Test
    fun jitter가_0이면_base_TTL을_그대로_반환한다() {
        val baseTtl = Duration.ofSeconds(300)

        val result = TtlJitter.apply(baseTtl, 0)

        assertEquals(300, result.seconds)
    }

    @Test
    fun 짧은_TTL에도_jitter가_적용된다() {
        val baseTtl = Duration.ofSeconds(20)
        val jitterPercent = 10

        repeat(100) {
            val result = TtlJitter.apply(baseTtl, jitterPercent)
            assertTrue(result.seconds in 20..22,
                "TTL ${result.seconds}s 가 범위 [20, 22] 밖입니다")
        }
    }
}
