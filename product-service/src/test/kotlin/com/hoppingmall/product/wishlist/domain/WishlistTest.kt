package com.hoppingmall.product.wishlist.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test

@DisplayName("Wishlist 도메인")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class WishlistTest {

    @Test
    fun 위시리스트를_생성한다() {
        val wishlist = Wishlist.create(buyerId = 1L, productId = 10L)

        assertThat(wishlist.buyerId).isEqualTo(1L)
        assertThat(wishlist.productId).isEqualTo(10L)
    }
}
