package com.hoppingmall.payment.point.exception.code

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test

@DisplayName("포인트 오류 코드 형식")
@DisplayNameGeneration(ReplaceUnderscores::class)
class PointErrorCodeFormatTest {

    @Test
    fun 포인트_오류_코드는_PNT_접두사와_세_자리_번호다() {
        assertThat(PointErrorCode.entries.map { it.code }).allMatch { Regex("^PNT[0-9]{3}$").matches(it) }
    }

    @Test
    fun 포인트_오류_코드는_서로_겹치지_않는다() {
        assertThat(PointErrorCode.entries.map { it.code }).doesNotHaveDuplicates()
    }
}
