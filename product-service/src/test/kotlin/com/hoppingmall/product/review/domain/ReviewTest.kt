package com.hoppingmall.product.review.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test

@DisplayName("Review 도메인")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class ReviewTest {

    @Test
    fun 리뷰를_생성한다() {
        val review = Review.create(
            buyerId = 1L, orderItemId = 100L, productId = 10L,
            rating = 5, content = "좋아요", imageUrl = "http://img.jpg"
        )

        assertThat(review.buyerId).isEqualTo(1L)
        assertThat(review.orderItemId).isEqualTo(100L)
        assertThat(review.productId).isEqualTo(10L)
        assertThat(review.rating).isEqualTo(5)
        assertThat(review.content).isEqualTo("좋아요")
        assertThat(review.imageUrl).isEqualTo("http://img.jpg")
    }

    @Test
    fun 이미지_없이_리뷰를_생성한다() {
        val review = Review.create(
            buyerId = 1L, orderItemId = 100L, productId = 10L,
            rating = 4, content = "괜찮아요"
        )

        assertThat(review.imageUrl).isNull()
    }

    @Test
    fun 리뷰를_수정한다() {
        val review = Review.create(
            buyerId = 1L, orderItemId = 100L, productId = 10L,
            rating = 5, content = "좋아요"
        )

        review.update(rating = 3, content = "보통이에요", imageUrl = "http://new.jpg")

        assertThat(review.rating).isEqualTo(3)
        assertThat(review.content).isEqualTo("보통이에요")
        assertThat(review.imageUrl).isEqualTo("http://new.jpg")
    }
}
