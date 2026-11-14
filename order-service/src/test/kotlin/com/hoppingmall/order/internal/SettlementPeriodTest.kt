package com.hoppingmall.order.internal

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

@DisplayName("정산 기간 시각을 앱 시간대 시각으로 바꾸기")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class SettlementPeriodTest {

    @Test
    fun 앱_시간대가_UTC면_한국_자정은_전날_15시다() {
        val start = SettlementPeriod.toApplicationTime(LocalDateTime.of(2026, 11, 14, 0, 0), ZoneOffset.UTC)
        val end = SettlementPeriod.toApplicationTime(LocalDateTime.of(2026, 11, 15, 0, 0), ZoneOffset.UTC)

        assertThat(start).isEqualTo(LocalDateTime.of(2026, 11, 13, 15, 0))
        assertThat(end).isEqualTo(LocalDateTime.of(2026, 11, 14, 15, 0))
    }

    @Test
    fun 앱_시간대가_한국이면_값이_그대로다() {
        val value = LocalDateTime.of(2026, 11, 14, 23, 59, 59, 500_000_000)

        assertThat(SettlementPeriod.toApplicationTime(value, ZoneId.of("Asia/Seoul"))).isEqualTo(value)
    }

    @Test
    fun 소수_초까지_그대로_옮긴다() {
        val value = LocalDateTime.of(2026, 11, 14, 23, 59, 59, 500_000_000)

        assertThat(SettlementPeriod.toApplicationTime(value, ZoneOffset.UTC))
            .isEqualTo(LocalDateTime.of(2026, 11, 14, 14, 59, 59, 500_000_000))
    }
}
