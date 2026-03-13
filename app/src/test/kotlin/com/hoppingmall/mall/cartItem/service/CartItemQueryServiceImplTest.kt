package com.hoppingmall.mall.cartItem.service

import com.hoppingmall.mall.cartItem.domain.CartItem
import com.hoppingmall.mall.cartItem.domain.repository.CartItemRepository
import com.hoppingmall.mall.cartItem.dto.response.CartItemResponse
import com.hoppingmall.mall.support.fixture.fixture
import com.hoppingmall.mall.support.withId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.SliceImpl

@DisplayName("CartItemQueryServiceImpl")
@DisplayNameGeneration(ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class CartItemQueryServiceImplTest {

    @Mock
    private lateinit var cartItemRepository: CartItemRepository

    private lateinit var cartItemQueryService: CartItemQueryServiceImpl

    @BeforeEach
    fun setUp() {
        cartItemQueryService = CartItemQueryServiceImpl(cartItemRepository)
    }

    @Nested
    @DisplayName("getCartItems")
    inner class GetCartItems {

        @Test
        fun 장바구니_아이템_조회_성공() {
            // Data
            val buyerId = 1L
            val pageable = PageRequest.of(0, 20)
            val cartItem = CartItem.fixture(buyerId = buyerId).withId(1L)
            val slice = SliceImpl(listOf(cartItem), pageable, false)

            // Context
            whenever(cartItemRepository.findByBuyerId(buyerId, pageable)).thenReturn(slice)

            // Interaction
            val result = cartItemQueryService.getCartItems(buyerId, pageable)

            // Assertions
            assertThat(result.content).hasSize(1)
            assertThat(result.content[0].productId).isEqualTo(cartItem.productId)
            assertThat(result.hasNext()).isFalse()

            verify(cartItemRepository).findByBuyerId(buyerId, pageable)
        }

        @Test
        fun 빈_장바구니_조회_성공() {
            // Data
            val buyerId = 1L
            val pageable = PageRequest.of(0, 20)

            // Context
            whenever(cartItemRepository.findByBuyerId(buyerId, pageable))
                .thenReturn(SliceImpl(emptyList(), pageable, false))

            // Interaction
            val result = cartItemQueryService.getCartItems(buyerId, pageable)

            // Assertions
            assertThat(result.content).isEmpty()
            assertThat(result.hasNext()).isFalse()

            verify(cartItemRepository).findByBuyerId(buyerId, pageable)
        }
    }
}
