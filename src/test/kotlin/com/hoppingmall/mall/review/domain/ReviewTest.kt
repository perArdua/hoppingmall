package com.hoppingmall.mall.review.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("Review")
@DisplayNameGeneration(ReplaceUnderscores::class)
class ReviewTest {

    @Nested
    @DisplayName("create")
    inner class Create {

        @Test
        fun 리뷰_생성_성공() {
            // Data
            val buyerId = 1L
            val orderItemId = 1L
            val productId = 100L
            val rating = 5
            val content = "정말 좋은 상품입니다. 배송도 빠르고 품질도 만족합니다."

            // Interaction
            val review = Review.create(
                buyerId = buyerId,
                orderItemId = orderItemId,
                productId = productId,
                rating = rating,
                content = content
            )

            // Assertions
            assertThat(review.buyerId).isEqualTo(buyerId)
            assertThat(review.orderItemId).isEqualTo(orderItemId)
            assertThat(review.productId).isEqualTo(productId)
            assertThat(review.rating).isEqualTo(rating)
            assertThat(review.content).isEqualTo(content)
            assertThat(review.imageUrl).isNull()
        }

        @Test
        fun 이미지_포함_리뷰_생성_성공() {
            // Data
            val imageUrl = "https://example.com/image.jpg"

            // Interaction
            val review = Review.create(
                buyerId = 1L,
                orderItemId = 1L,
                productId = 100L,
                rating = 4,
                content = "이미지 포함 리뷰 테스트입니다. 상품이 좋습니다.",
                imageUrl = imageUrl
            )

            // Assertions
            assertThat(review.imageUrl).isEqualTo(imageUrl)
        }
    }

    @Nested
    @DisplayName("update")
    inner class Update {

        @Test
        fun 리뷰_수정_성공() {
            // Data
            val review = Review.create(
                buyerId = 1L,
                orderItemId = 1L,
                productId = 100L,
                rating = 3,
                content = "보통입니다. 그냥 그런 상품이에요."
            )

            // Interaction
            review.update(
                rating = 5,
                content = "다시 사용해보니 정말 좋은 상품입니다. 강력 추천합니다.",
                imageUrl = "https://example.com/updated.jpg"
            )

            // Assertions
            assertThat(review.rating).isEqualTo(5)
            assertThat(review.content).isEqualTo("다시 사용해보니 정말 좋은 상품입니다. 강력 추천합니다.")
            assertThat(review.imageUrl).isEqualTo("https://example.com/updated.jpg")
        }
    }
}
