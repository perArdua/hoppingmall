package com.hoppingmall.mall.product.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("ProductImage")
@DisplayNameGeneration(ReplaceUnderscores::class)
class ProductImageTest {

    @Nested
    @DisplayName("create")
    inner class Create {
        @Test
        fun 상품_이미지_생성_성공() {
            val productId = 1L
            val imageUrl = "https://example.com/image.jpg"

            val productImage = ProductImage.create(productId, imageUrl)

            assertEquals(productId, productImage.productId)
            assertEquals(imageUrl, productImage.imageUrl)
        }
    }
} 