package com.hoppingmall.order.cartItem.exception

import com.hoppingmall.common.CommonErrorCode
import com.hoppingmall.order.cartItem.exception.code.CartItemErrorCode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test

@DisplayName("장바구니 오류 코드")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class CartItemErrorCodeTest {

    @Test
    fun 장바구니_오류_코드는_공통_오류_코드와_겹치지_않는다() {
        val commonCodes = CommonErrorCode.entries.map { it.code }.toSet()

        assertThat(CartItemErrorCode.entries.map { it.code }).doesNotContainAnyElementsOf(commonCodes)
    }

    @Test
    fun 장바구니_오류_코드는_장바구니_전용_접두사를_쓴다() {
        assertThat(CartItemErrorCode.entries.map { it.code }).allSatisfy {
            assertThat(it).matches("CART\\d{3}")
        }
    }
}
