package com.hoppingmall.mall.cartItem.service

import com.hoppingmall.mall.cartItem.domain.CartItem
import com.hoppingmall.mall.cartItem.domain.repository.CartItemRepository
import com.hoppingmall.mall.cartItem.dto.response.CartItemResponse
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
        fun 빈_장바구니_조회_성공() {
            // Data
            val buyerId = 1L

            // Context
            whenever(cartItemRepository.findByBuyerId(buyerId)).thenReturn(emptyList())

            // Interaction
            val result = cartItemQueryService.getCartItems(buyerId)

            // Assertions
            assertThat(result).isEmpty()

            verify(cartItemRepository).findByBuyerId(buyerId)
        }
    }
} 