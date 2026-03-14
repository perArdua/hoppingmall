package com.hoppingmall.mall.wishlist.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test

@DisplayName("Wishlist")
@DisplayNameGeneration(ReplaceUnderscores::class)
class WishlistTest {

    @Test
    fun 위시리스트_생성_성공() {
        // Data
        val buyerId = 1L
        val productId = 100L

        // Interaction
        val wishlist = Wishlist.create(buyerId, productId)

        // Assertions
        assertThat(wishlist.buyerId).isEqualTo(buyerId)
        assertThat(wishlist.productId).isEqualTo(productId)
    }
}
