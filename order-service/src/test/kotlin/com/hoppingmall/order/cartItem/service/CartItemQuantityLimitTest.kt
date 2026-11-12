package com.hoppingmall.order.cartItem.service

import com.hoppingmall.order.cartItem.domain.CartItem
import com.hoppingmall.order.cartItem.domain.repository.CartItemRepository
import com.hoppingmall.order.cartItem.dto.request.CartItemCreateRequest
import com.hoppingmall.order.cartItem.dto.request.CartItemUpdateRequest
import com.hoppingmall.order.cartItem.exception.CartItemInvalidQuantityException
import com.hoppingmall.order.port.ProductInfo
import com.hoppingmall.order.port.ProductQueryPort
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.test.util.ReflectionTestUtils
import java.math.BigDecimal
import java.util.Optional

@DisplayName("장바구니 수량 상한")
@DisplayNameGeneration(ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class CartItemQuantityLimitTest {

    @Mock
    private lateinit var cartItemRepository: CartItemRepository

    @Mock
    private lateinit var productQueryPort: ProductQueryPort

    @InjectMocks
    private lateinit var service: CartItemCommandServiceImpl

    @Test
    fun 큰_수량을_두_번_담아_합이_Int_범위를_넘으면_거절하고_수량이_음수가_되지_않는다() {
        val existing = stubExistingCartItem(quantity = 2_000_000_000)

        val result = runCatching { service.addCartItem(BUYER_ID, CartItemCreateRequest(productId = PRODUCT_ID, quantity = 2_000_000_000)) }

        assertThat(existing.quantity).isEqualTo(2_000_000_000)
        assertThat(result.exceptionOrNull()).isInstanceOf(CartItemInvalidQuantityException::class.java)
    }

    @Test
    fun 담긴_수량에_더한_합이_한도를_넘으면_거절하고_기존_수량을_그대로_둔다() {
        val existing = stubExistingCartItem(quantity = 600_000_000)

        val result = runCatching { service.addCartItem(BUYER_ID, CartItemCreateRequest(productId = PRODUCT_ID, quantity = 400_000_000)) }

        assertThat(existing.quantity).isEqualTo(600_000_000)
        assertThat(existing.totalPrice).isEqualByComparingTo(PRICE.multiply(BigDecimal(600_000_000)))
        assertThat(result.exceptionOrNull()).isInstanceOf(CartItemInvalidQuantityException::class.java)
    }

    @Test
    fun 한도보다_큰_수량을_새로_담으면_거절하고_저장하지_않는다() {
        whenever(productQueryPort.findProductById(PRODUCT_ID)).thenReturn(product())
        whenever(cartItemRepository.findByBuyerIdAndProductId(BUYER_ID, PRODUCT_ID)).thenReturn(null)
        Mockito.lenient().`when`(cartItemRepository.save(any<CartItem>())).thenAnswer { invocation ->
            (invocation.arguments[0] as CartItem).also { ReflectionTestUtils.setField(it, "id", CART_ITEM_ID) }
        }

        val result = runCatching { service.addCartItem(BUYER_ID, CartItemCreateRequest(productId = PRODUCT_ID, quantity = 1_000_000_000)) }

        assertThat(result.exceptionOrNull()).isInstanceOf(CartItemInvalidQuantityException::class.java)
        verify(cartItemRepository, never()).save(any<CartItem>())
    }

    @Test
    fun 수량을_한도보다_크게_바꾸면_거절하고_수량을_그대로_둔다() {
        val cartItem = cartItem(quantity = 3)
        whenever(cartItemRepository.findById(CART_ITEM_ID)).thenReturn(Optional.of(cartItem))

        val result = runCatching { service.updateCartItemQuantity(BUYER_ID, CART_ITEM_ID, CartItemUpdateRequest(quantity = 1_000_000_000)) }

        assertThat(cartItem.quantity).isEqualTo(3)
        assertThat(result.exceptionOrNull()).isInstanceOf(CartItemInvalidQuantityException::class.java)
    }

    @Test
    fun 한도와_같은_수량은_더해_담을_수_있고_그_값으로_바꿀_수_있다() {
        val existing = stubExistingCartItem(quantity = 999_999_998)

        val added = service.addCartItem(BUYER_ID, CartItemCreateRequest(productId = PRODUCT_ID, quantity = 1))

        assertThat(added.quantity).isEqualTo(999_999_999)

        val other = cartItem(quantity = 1)
        whenever(cartItemRepository.findById(CART_ITEM_ID)).thenReturn(Optional.of(other))

        val updated = service.updateCartItemQuantity(BUYER_ID, CART_ITEM_ID, CartItemUpdateRequest(quantity = 999_999_999))

        assertThat(updated.quantity).isEqualTo(999_999_999)
        assertThat(existing.quantity).isEqualTo(999_999_999)
    }

    private fun stubExistingCartItem(quantity: Int): CartItem {
        val existing = cartItem(quantity = quantity)
        whenever(productQueryPort.findProductById(PRODUCT_ID)).thenReturn(product())
        whenever(cartItemRepository.findByBuyerIdAndProductId(BUYER_ID, PRODUCT_ID)).thenReturn(existing)
        return existing
    }

    private fun product() = ProductInfo(id = PRODUCT_ID, name = "상품", price = PRICE, sellerId = 5L, imageUrl = null)

    private fun cartItem(quantity: Int): CartItem {
        val cartItem = CartItem.create(
            buyerId = BUYER_ID,
            productId = PRODUCT_ID,
            productName = "상품",
            productPrice = PRICE,
            productImageUrl = null,
            quantity = quantity
        )
        ReflectionTestUtils.setField(cartItem, "id", CART_ITEM_ID)
        return cartItem
    }

    companion object {
        private const val BUYER_ID = 1L
        private const val PRODUCT_ID = 100L
        private const val CART_ITEM_ID = 1L
        private val PRICE = BigDecimal("10000")
    }
}
