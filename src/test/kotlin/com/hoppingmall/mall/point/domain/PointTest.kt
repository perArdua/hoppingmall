package com.hoppingmall.mall.point.domain

import com.hoppingmall.mall.support.fixture.fixture
import com.hoppingmall.mall.support.fixture.emptyFixture
import com.hoppingmall.mall.support.fixture.richFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.math.BigDecimal

@DisplayName("Point")
@DisplayNameGeneration(ReplaceUnderscores::class)
class PointTest {

    @Nested
    @DisplayName("생성")
    inner class Creation {
        @Test
        fun 포인트_생성_성공() {
            val userId = 1L
            val balance = BigDecimal("1000")

            val point = Point.fixture(userId = userId, balance = balance)

            assertEquals(userId, point.userId)
            assertEquals(balance, point.balance)
        }

        @Test
        fun 기본값으로_포인트_생성_성공() {
            val userId = 1L

            val point = Point.fixture(userId = userId)

            assertEquals(userId, point.userId)
            assertEquals(BigDecimal("1000"), point.balance)
        }

        @Test
        fun 빈_포인트_생성_성공() {
            val userId = 1L

            val point = Point.emptyFixture(userId = userId)

            assertEquals(userId, point.userId)
            assertEquals(BigDecimal.ZERO, point.balance)
        }
    }

    @Nested
    @DisplayName("addPoints")
    inner class AddPoints {
        @Test
        fun 포인트_적립_성공() {
            val point = Point.fixture()
            val addAmount = BigDecimal("500")

            point.addPoints(addAmount)

            assertEquals(BigDecimal("1500"), point.balance)
        }

        @Test
        fun 음수_포인트_적립_시_예외_발생() {
            val point = Point.fixture()
            val negativeAmount = BigDecimal("-500")

            assertThrows(IllegalArgumentException::class.java) {
                point.addPoints(negativeAmount)
            }
        }
    }

    @Nested
    @DisplayName("usePoints")
    inner class UsePoints {
        @Test
        fun 포인트_사용_성공() {
            val point = Point.fixture()
            val useAmount = BigDecimal("500")

            point.usePoints(useAmount)

            assertEquals(BigDecimal("500"), point.balance)
        }

        @Test
        fun 잔액_부족_시_예외_발생() {
            val point = Point.fixture()
            val useAmount = BigDecimal("1500")

            assertThrows(IllegalArgumentException::class.java) {
                point.usePoints(useAmount)
            }
        }

        @Test
        fun 음수_포인트_사용_시_예외_발생() {
            val point = Point.fixture()
            val negativeAmount = BigDecimal("-500")

            assertThrows(IllegalArgumentException::class.java) {
                point.usePoints(negativeAmount)
            }
        }

        @Test
        fun 풍부한_포인트로_사용_성공() {
            val point = Point.richFixture()
            val useAmount = BigDecimal("5000")

            point.usePoints(useAmount)

            assertEquals(BigDecimal("5000"), point.balance)
        }
    }
} 