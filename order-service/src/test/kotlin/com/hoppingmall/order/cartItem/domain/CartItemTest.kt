package com.hoppingmall.order.cartItem.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import java.math.BigDecimal

@DisplayName("CartItem")
@DisplayNameGeneration(ReplaceUnderscores::class)
class CartItemTest {

    @Test
    fun 장바구니_항목을_생성한다() {
        val cartItem = CartItem.create(
            buyerId = 1L,
            productId = 100L,
            productName = "테스트 상품",
            productPrice = BigDecimal("10000"),
            productImageUrl = "http://example.com/image.jpg",
            quantity = 3
        )

        assertThat(cartItem.buyerId).isEqualTo(1L)
        assertThat(cartItem.productId).isEqualTo(100L)
        assertThat(cartItem.productName).isEqualTo("테스트 상품")
        assertThat(cartItem.productPrice).isEqualByComparingTo(BigDecimal("10000"))
        assertThat(cartItem.productImageUrl).isEqualTo("http://example.com/image.jpg")
        assertThat(cartItem.quantity).isEqualTo(3)
        assertThat(cartItem.totalPrice).isEqualByComparingTo(BigDecimal("30000"))
    }

    @Test
    fun 이미지URL_없이_장바구니_항목을_생성한다() {
        val cartItem = CartItem.create(
            buyerId = 1L,
            productId = 100L,
            productName = "테스트 상품",
            productPrice = BigDecimal("5000"),
            productImageUrl = null,
            quantity = 2
        )

        assertThat(cartItem.productImageUrl).isNull()
        assertThat(cartItem.totalPrice).isEqualByComparingTo(BigDecimal("10000"))
    }

    @Test
    fun 수량을_변경하면_총가격이_재계산된다() {
        val cartItem = CartItem.create(
            buyerId = 1L,
            productId = 100L,
            productName = "테스트 상품",
            productPrice = BigDecimal("10000"),
            productImageUrl = null,
            quantity = 1
        )

        cartItem.updateQuantity(5)

        assertThat(cartItem.quantity).isEqualTo(5)
        assertThat(cartItem.totalPrice).isEqualByComparingTo(BigDecimal("50000"))
    }
}
