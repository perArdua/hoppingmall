package com.hoppingmall.mall.support.fixture

import com.hoppingmall.mall.review.domain.Review
import com.hoppingmall.mall.support.withId

fun Review.Companion.fixture(
    buyerId: Long = 1L,
    orderItemId: Long = 1L,
    productId: Long = 100L,
    rating: Int = 5,
    content: String = "정말 좋은 상품입니다. 배송도 빠르고 품질도 만족합니다.",
    imageUrl: String? = null
): Review {
    return Review.create(
        buyerId = buyerId,
        orderItemId = orderItemId,
        productId = productId,
        rating = rating,
        content = content,
        imageUrl = imageUrl
    ).withId(1L)
}
