package com.hoppingmall.order.cartItem.service

import com.hoppingmall.order.cartItem.domain.CartItem
import com.hoppingmall.order.cartItem.domain.repository.CartItemRepository
import com.hoppingmall.order.cartItem.dto.request.CartItemCreateRequest
import com.hoppingmall.order.cartItem.dto.request.CartItemUpdateRequest
import com.hoppingmall.order.cartItem.exception.CartItemAccessDeniedException
import com.hoppingmall.order.cartItem.exception.CartItemNotFoundException
import com.hoppingmall.order.cartItem.exception.CartItemProductNotFoundException
import com.hoppingmall.order.port.ProductInfo
import com.hoppingmall.order.port.ProductQueryPort
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.test.util.ReflectionTestUtils
import java.math.BigDecimal
import java.util.Optional

@DisplayName("CartItemCommandServiceImpl")
@DisplayNameGeneration(ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class CartItemCommandServiceImplTest {

    @Mock
    private lateinit var cartItemRepository: CartItemRepository

    @Mock
    private lateinit var productQueryPort: ProductQueryPort

    @InjectMocks
    private lateinit var service: CartItemCommandServiceImpl

    private fun createProductInfo(id: Long = 100L): ProductInfo {
        return ProductInfo(
            id = id,
            name = "테스트 상품",
            price = BigDecimal("10000"),
            sellerId = 5L,
            imageUrl = "http://example.com/image.jpg"
        )
    }

    private fun createCartItem(id: Long = 1L, buyerId: Long = 1L, productId: Long = 100L, quantity: Int = 2): CartItem {
        val cartItem = CartItem.create(
            buyerId = buyerId,
            productId = productId,
            productName = "테스트 상품",
            productPrice = BigDecimal("10000"),
            productImageUrl = null,
            quantity = quantity
        )
        ReflectionTestUtils.setField(cartItem, "id", id)
        return cartItem
    }

    @Test
    fun 새_상품을_장바구니에_추가한다() {
        val request = CartItemCreateRequest(productId = 100L, quantity = 2)
        val product = createProductInfo()
        val cartItem = createCartItem()

        whenever(productQueryPort.findProductById(100L)).thenReturn(product)
        whenever(cartItemRepository.findByBuyerIdAndProductId(1L, 100L)).thenReturn(null)
        whenever(cartItemRepository.save(any<CartItem>())).thenReturn(cartItem)

        val result = service.addCartItem(1L, request)

        assertThat(result.productId).isEqualTo(100L)
    }

    @Test
    fun 이미_존재하는_상품을_추가하면_수량이_증가한다() {
        val request = CartItemCreateRequest(productId = 100L, quantity = 3)
        val product = createProductInfo()
        val existingCartItem = createCartItem(quantity = 2)

        whenever(productQueryPort.findProductById(100L)).thenReturn(product)
        whenever(cartItemRepository.findByBuyerIdAndProductId(1L, 100L)).thenReturn(existingCartItem)

        val result = service.addCartItem(1L, request)

        assertThat(result.quantity).isEqualTo(5)
    }

    @Test
    fun 존재하지_않는_상품_추가_시_예외가_발생한다() {
        val request = CartItemCreateRequest(productId = 999L, quantity = 1)

        whenever(productQueryPort.findProductById(999L)).thenReturn(null)

        assertThatThrownBy { service.addCartItem(1L, request) }
            .isInstanceOf(CartItemProductNotFoundException::class.java)
    }

    @Test
    fun 장바구니_수량을_변경한다() {
        val request = CartItemUpdateRequest(quantity = 5)
        val cartItem = createCartItem(buyerId = 1L)

        whenever(cartItemRepository.findById(1L)).thenReturn(Optional.of(cartItem))

        val result = service.updateCartItemQuantity(1L, 1L, request)

        assertThat(result.quantity).isEqualTo(5)
    }

    @Test
    fun 존재하지_않는_장바구니_수량변경_시_예외가_발생한다() {
        val request = CartItemUpdateRequest(quantity = 5)

        whenever(cartItemRepository.findById(999L)).thenReturn(Optional.empty())

        assertThatThrownBy { service.updateCartItemQuantity(1L, 999L, request) }
            .isInstanceOf(CartItemNotFoundException::class.java)
    }

    @Test
    fun 다른_사용자의_장바구니_수량변경_시_예외가_발생한다() {
        val request = CartItemUpdateRequest(quantity = 5)
        val cartItem = createCartItem(buyerId = 99L)

        whenever(cartItemRepository.findById(1L)).thenReturn(Optional.of(cartItem))

        assertThatThrownBy { service.updateCartItemQuantity(1L, 1L, request) }
            .isInstanceOf(CartItemAccessDeniedException::class.java)
    }

    @Test
    fun 장바구니에서_상품을_삭제한다() {
        val cartItem = createCartItem(buyerId = 1L)

        whenever(cartItemRepository.findById(1L)).thenReturn(Optional.of(cartItem))

        service.removeCartItem(1L, 1L)

        assertThat(cartItem.deletedAt).isNotNull()
    }

    @Test
    fun 존재하지_않는_장바구니_삭제_시_예외가_발생한다() {
        whenever(cartItemRepository.findById(999L)).thenReturn(Optional.empty())

        assertThatThrownBy { service.removeCartItem(1L, 999L) }
            .isInstanceOf(CartItemNotFoundException::class.java)
    }

    @Test
    fun 다른_사용자의_장바구니_삭제_시_예외가_발생한다() {
        val cartItem = createCartItem(buyerId = 99L)

        whenever(cartItemRepository.findById(1L)).thenReturn(Optional.of(cartItem))

        assertThatThrownBy { service.removeCartItem(1L, 1L) }
            .isInstanceOf(CartItemAccessDeniedException::class.java)
    }
}
