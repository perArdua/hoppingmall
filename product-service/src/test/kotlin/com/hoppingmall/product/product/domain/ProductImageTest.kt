package com.hoppingmall.product.product.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test

@DisplayName("ProductImage 도메인")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class ProductImageTest {

    @Test
    fun 상품_이미지를_생성한다() {
        val image = ProductImage.create(productId = 1L, imageUrl = "http://img.jpg", sortOrder = 0)

        assertThat(image.productId).isEqualTo(1L)
        assertThat(image.imageUrl).isEqualTo("http://img.jpg")
        assertThat(image.sortOrder).isEqualTo(0)
    }

    @Test
    fun 기본_정렬순서로_생성한다() {
        val image = ProductImage.create(productId = 1L, imageUrl = "http://img.jpg")

        assertThat(image.sortOrder).isEqualTo(0)
    }
}
