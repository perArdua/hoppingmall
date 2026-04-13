package com.hoppingmall.payment.point.domain

import com.hoppingmall.payment.point.exception.PointInsufficientBalanceException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import java.math.BigDecimal

@DisplayName("Point")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class PointTest {

    @Test
    fun 포인트_생성_시_초기_잔액은_0이다() {
        val point = Point(userId = 1L)

        assertThat(point.userId).isEqualTo(1L)
        assertThat(point.balance).isEqualByComparingTo(BigDecimal.ZERO)
    }

    @Test
    fun 포인트를_추가하면_잔액이_증가한다() {
        val point = Point(userId = 1L, balance = BigDecimal("1000"))

        point.addPoints(BigDecimal("500"))

        assertThat(point.balance).isEqualByComparingTo(BigDecimal("1500"))
    }

    @Test
    fun 음수_포인트를_추가하면_예외가_발생한다() {
        val point = Point(userId = 1L)

        assertThatThrownBy { point.addPoints(BigDecimal("-100")) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun 포인트를_사용하면_잔액이_감소한다() {
        val point = Point(userId = 1L, balance = BigDecimal("1000"))

        point.usePoints(BigDecimal("300"))

        assertThat(point.balance).isEqualByComparingTo(BigDecimal("700"))
    }

    @Test
    fun 잔액보다_많은_포인트를_사용하면_예외가_발생한다() {
        val point = Point(userId = 1L, balance = BigDecimal("100"))

        assertThatThrownBy { point.usePoints(BigDecimal("200")) }
            .isInstanceOf(PointInsufficientBalanceException::class.java)
    }

    @Test
    fun 음수_포인트를_사용하면_예외가_발생한다() {
        val point = Point(userId = 1L, balance = BigDecimal("1000"))

        assertThatThrownBy { point.usePoints(BigDecimal("-100")) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }
}
