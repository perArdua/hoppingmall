package com.hoppingmall.order.cartItem.service

import com.hoppingmall.order.cartItem.domain.CartItem
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
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.SliceImpl
import org.springframework.test.util.ReflectionTestUtils
import java.math.BigDecimal

@DisplayName("CartItemQueryServiceImpl")
@DisplayNameGeneration(ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class CartItemQueryServiceImplTest {

    @Mock
    private lateinit var cartItemRepository: CartItemRepository

    @InjectMocks
    private lateinit var service: CartItemQueryServiceImpl

    private fun createCartItem(id: Long = 1L, buyerId: Long = 1L): CartItem {
        val cartItem = CartItem.create(
            buyerId = buyerId,
            productId = 100L,
            productName = "테스트 상품",
            productPrice = BigDecimal("10000"),
            productImageUrl = null,
            quantity = 2
        )
        ReflectionTestUtils.setField(cartItem, "id", id)
        return cartItem
    }

    @Test
    fun 구매자의_장바구니_목록을_조회한다() {
        val pageable = PageRequest.of(0, 20)
        val cartItems = listOf(createCartItem())
        val slice = SliceImpl(cartItems, pageable, false)

        whenever(cartItemRepository.findByBuyerId(1L, pageable)).thenReturn(slice)

        val result = service.getCartItems(1L, pageable)

        assertThat(result.content).hasSize(1)
        assertThat(result.content[0].productId).isEqualTo(100L)
    }

    @Test
    fun 장바구니가_비어있으면_빈_목록을_반환한다() {
        val pageable = PageRequest.of(0, 20)
        val emptySlice = SliceImpl(emptyList<CartItem>(), pageable, false)

        whenever(cartItemRepository.findByBuyerId(1L, pageable)).thenReturn(emptySlice)

        val result = service.getCartItems(1L, pageable)

        assertThat(result.content).isEmpty()
    }
}
