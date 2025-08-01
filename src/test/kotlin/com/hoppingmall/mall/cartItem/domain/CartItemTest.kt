package com.hoppingmall.mall.cartItem.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test

@DisplayName("CartItem")
@DisplayNameGeneration(ReplaceUnderscores::class)
class CartItemTest {

    @Test
    fun 장바구니_아이템_생성_성공() {
        // Data
        val buyerId = 1L
        val productId = 100L
        val productName = "테스트 상품"
        val productPrice = 15000L
        val productImageUrl = "https://example.com/image.jpg"
        val quantity = 2

        // Interaction
        val cartItem = CartItem.create(
            buyerId, 
            productId, 
            productName, 
            productPrice, 
            productImageUrl, 
            quantity
        )

        // Assertions
        assertThat(cartItem.buyerId).isEqualTo(buyerId)
        assertThat(cartItem.productId).isEqualTo(productId)
        assertThat(cartItem.productName).isEqualTo(productName)
        assertThat(cartItem.productPrice).isEqualTo(productPrice)
        assertThat(cartItem.productImageUrl).isEqualTo(productImageUrl)
        assertThat(cartItem.quantity).isEqualTo(quantity)
        assertThat(cartItem.totalPrice).isEqualTo(productPrice * quantity)
    }

    @Test
    fun 장바구니_아이템_수량_변경_성공() {
        // Data
        val cartItem = CartItem.create(
            1L, 
            100L, 
            "테스트 상품", 
            15000L, 
            "https://example.com/image.jpg", 
            2
        )
        val newQuantity = 5

        // Interaction
        val updatedCartItem = CartItem.create(
            cartItem.buyerId,
            cartItem.productId,
            cartItem.productName,
            cartItem.productPrice,
            cartItem.productImageUrl,
            newQuantity
        )

        // Assertions
        assertThat(updatedCartItem.quantity).isEqualTo(newQuantity)
        assertThat(updatedCartItem.totalPrice).isEqualTo(cartItem.productPrice * newQuantity)
        assertThat(updatedCartItem.buyerId).isEqualTo(cartItem.buyerId)
        assertThat(updatedCartItem.productId).isEqualTo(cartItem.productId)
        assertThat(updatedCartItem.productName).isEqualTo(cartItem.productName)
        assertThat(updatedCartItem.productPrice).isEqualTo(cartItem.productPrice)
    }

    @Test
    fun 장바구니_아이템_이미지_없는_상품_생성_성공() {
        // Data
        val buyerId = 1L
        val productId = 100L
        val productName = "이미지 없는 상품"
        val productPrice = 10000L
        val productImageUrl = null
        val quantity = 1

        // Interaction
        val cartItem = CartItem.create(
            buyerId, 
            productId, 
            productName, 
            productPrice, 
            productImageUrl, 
            quantity
        )

        // Assertions
        assertThat(cartItem.productImageUrl).isNull()
        assertThat(cartItem.totalPrice).isEqualTo(productPrice * quantity)
    }
} 