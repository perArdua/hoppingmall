package com.hoppingmall.order.internal

import com.hoppingmall.order.cartItem.dto.CartAggregation
import com.hoppingmall.order.cartItem.domain.repository.CartItemRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus

@DisplayName("InternalCartItemController")
@DisplayNameGeneration(ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class InternalCartItemControllerTest {

    @Mock
    private lateinit var cartItemRepository: CartItemRepository

    @InjectMocks
    private lateinit var controller: InternalCartItemController

    @Test
    fun 장바구니_상품별_구매자수_집계를_조회한다() {
        val aggregation1 = object : CartAggregation {
            override fun getProductId(): Long = 100L
            override fun getBuyerCount(): Long = 5L
        }
        val aggregation2 = object : CartAggregation {
            override fun getProductId(): Long = 200L
            override fun getBuyerCount(): Long = 3L
        }

        whenever(cartItemRepository.aggregateCartByProduct()).thenReturn(listOf(aggregation1, aggregation2))

        val response = controller.aggregateCartByProduct()

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body).hasSize(2)
        assertThat(response.body!![0].productId).isEqualTo(100L)
        assertThat(response.body!![0].buyerCount).isEqualTo(5L)
        assertThat(response.body!![1].productId).isEqualTo(200L)
        assertThat(response.body!![1].buyerCount).isEqualTo(3L)
    }

    @Test
    fun 장바구니_집계가_없으면_빈_목록을_반환한다() {
        whenever(cartItemRepository.aggregateCartByProduct()).thenReturn(emptyList())

        val response = controller.aggregateCartByProduct()

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body).isEmpty()
    }
}
