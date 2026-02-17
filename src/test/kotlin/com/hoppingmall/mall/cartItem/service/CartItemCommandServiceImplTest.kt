package com.hoppingmall.mall.cartItem.service

import com.hoppingmall.mall.cartItem.domain.CartItem
import com.hoppingmall.mall.cartItem.domain.repository.CartItemRepository
import java.math.BigDecimal
import com.hoppingmall.mall.cartItem.dto.request.CartItemCreateRequest
import com.hoppingmall.mall.cartItem.dto.request.CartItemUpdateRequest
import com.hoppingmall.mall.cartItem.dto.response.CartItemResponse
import com.hoppingmall.mall.cartItem.exception.CartItemAccessDeniedException
import com.hoppingmall.mall.cartItem.exception.CartItemNotFoundException
import com.hoppingmall.mall.product.domain.Product
import com.hoppingmall.mall.product.domain.ProductImage
import com.hoppingmall.mall.product.domain.repository.ProductImageRepository
import com.hoppingmall.mall.product.domain.repository.ProductRepository
import com.hoppingmall.mall.product.exception.ProductNotFoundException
import com.hoppingmall.mall.support.fixture.fixture
import com.hoppingmall.mall.support.withId
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
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

@DisplayName("CartItemCommandServiceImpl")
@DisplayNameGeneration(ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class CartItemCommandServiceImplTest {

    @Mock
    private lateinit var cartItemRepository: CartItemRepository

    @Mock
    private lateinit var productRepository: ProductRepository

    @Mock
    private lateinit var productImageRepository: ProductImageRepository

    private lateinit var cartItemCommandService: CartItemCommandServiceImpl

    @BeforeEach
    fun setUp() {
        cartItemCommandService = CartItemCommandServiceImpl(cartItemRepository, productRepository, productImageRepository)
    }

    @Nested
    @DisplayName("addCartItem")
    inner class AddCartItem {

        @Test
        fun 새로운_장바구니_아이템_추가_성공() {
            // Data
            val buyerId = 1L
            val request = CartItemCreateRequest(productId = 100L, quantity = 2)
            val product = Product.fixture().withId(100L)
            val productImage = ProductImage.fixture(productId = 100L)

            // Context
            whenever(productRepository.findById(request.productId)).thenReturn(java.util.Optional.of(product))
            whenever(productImageRepository.findByProductId(request.productId)).thenReturn(productImage)
            whenever(cartItemRepository.findByBuyerIdAndProductId(buyerId, request.productId)).thenReturn(null)
            whenever(cartItemRepository.save(any<CartItem>())).thenAnswer { invocation ->
                val cartItem = invocation.getArgument<CartItem>(0)
                cartItem.withId(1L)
            }

            // Interaction
            val result = cartItemCommandService.addCartItem(buyerId, request)

            // Assertions
            assertThat(result).isNotNull()
            assertThat(result.productId).isEqualTo(request.productId)
            assertThat(result.productName).isEqualTo(product.name)
            assertThat(result.productPrice).isEqualTo(product.price)
            assertThat(result.productImageUrl).isEqualTo(productImage.imageUrl)
            assertThat(result.quantity).isEqualTo(request.quantity)
            assertThat(result.totalPrice).isEqualByComparingTo(product.price.multiply(BigDecimal(request.quantity)))

            verify(cartItemRepository).save(any())
        }

        @Test
        fun 기존_장바구니_아이템_수량_증가_성공() {
            // Data
            val buyerId = 1L
            val request = CartItemCreateRequest(productId = 100L, quantity = 3)
            val product = Product.fixture().withId(100L)
            val productImage = ProductImage.fixture(productId = 100L)
            val existingCartItem = CartItem.fixture(buyerId = buyerId, productId = 100L, quantity = 2).withId(1L)

            // Context
            whenever(productRepository.findById(request.productId)).thenReturn(java.util.Optional.of(product))
            whenever(productImageRepository.findByProductId(request.productId)).thenReturn(productImage)
            whenever(cartItemRepository.findByBuyerIdAndProductId(buyerId, request.productId)).thenReturn(existingCartItem)
            whenever(cartItemRepository.save(any<CartItem>())).thenAnswer { invocation ->
                val cartItem = invocation.getArgument<CartItem>(0)
                cartItem.withId(1L)
            }

            // Interaction
            val result = cartItemCommandService.addCartItem(buyerId, request)

            // Assertions
            assertThat(result.quantity).isEqualTo(5) // 2 + 3
            assertThat(result.totalPrice).isEqualByComparingTo(existingCartItem.productPrice.multiply(BigDecimal(5)))

            verify(cartItemRepository).save(any())
        }

        @Test
        fun 이미지가_없는_상품_추가_성공() {
            // Data
            val buyerId = 1L
            val request = CartItemCreateRequest(productId = 100L, quantity = 1)
            val product = Product.fixture().withId(100L)

            // Context
            whenever(productRepository.findById(request.productId)).thenReturn(java.util.Optional.of(product))
            whenever(productImageRepository.findByProductId(request.productId)).thenReturn(null)
            whenever(cartItemRepository.findByBuyerIdAndProductId(buyerId, request.productId)).thenReturn(null)
            whenever(cartItemRepository.save(any<CartItem>())).thenAnswer { invocation ->
                val cartItem = invocation.getArgument<CartItem>(0)
                cartItem.withId(1L)
            }

            // Interaction
            val result = cartItemCommandService.addCartItem(buyerId, request)

            // Assertions
            assertThat(result.productImageUrl).isNull()
            assertThat(result.totalPrice).isEqualByComparingTo(product.price.multiply(BigDecimal(request.quantity)))

            verify(cartItemRepository).save(any())
        }

        @Test
        fun 존재하지_않는_상품_추가_시_예외_발생() {
            // Data
            val buyerId = 1L
            val request = CartItemCreateRequest(productId = 999L, quantity = 1)

            // Context
            whenever(productRepository.findById(request.productId)).thenReturn(java.util.Optional.empty())

            // Interaction & Assertions
            assertThatThrownBy { cartItemCommandService.addCartItem(buyerId, request) }
                .isInstanceOf(ProductNotFoundException::class.java)
        }
    }

    @Nested
    @DisplayName("updateCartItemQuantity")
    inner class UpdateCartItemQuantity {

        @Test
        fun 장바구니_아이템_수량_수정_성공() {
            // Data
            val buyerId = 1L
            val cartItemId = 1L
            val request = CartItemUpdateRequest(quantity = 5)
            val existingCartItem = CartItem.fixture(buyerId = buyerId, productId = 100L, quantity = 2).withId(1L)

            // Context
            whenever(cartItemRepository.findById(cartItemId)).thenReturn(java.util.Optional.of(existingCartItem))
            whenever(cartItemRepository.save(any<CartItem>())).thenAnswer { invocation ->
                val cartItem = invocation.getArgument<CartItem>(0)
                cartItem.withId(1L)
            }

            // Interaction
            val result = cartItemCommandService.updateCartItemQuantity(buyerId, cartItemId, request)

            // Assertions
            assertThat(result.quantity).isEqualTo(request.quantity)
            assertThat(result.totalPrice).isEqualByComparingTo(existingCartItem.productPrice.multiply(BigDecimal(request.quantity)))

            verify(cartItemRepository).save(any())
        }

        @Test
        fun 존재하지_않는_장바구니_아이템_수정_시_예외_발생() {
            // Data
            val buyerId = 1L
            val cartItemId = 999L
            val request = CartItemUpdateRequest(quantity = 5)

            // Context
            whenever(cartItemRepository.findById(cartItemId)).thenReturn(java.util.Optional.empty())

            // Interaction & Assertions
            assertThatThrownBy { cartItemCommandService.updateCartItemQuantity(buyerId, cartItemId, request) }
                .isInstanceOf(CartItemNotFoundException::class.java)
        }

        @Test
        fun 다른_사용자의_장바구니_수정_시_예외_발생() {
            // Data
            val buyerId = 1L
            val cartItemId = 1L
            val request = CartItemUpdateRequest(quantity = 5)
            val existingCartItem = CartItem.fixture(buyerId = 2L, productId = 100L, quantity = 2).withId(1L)

            // Context
            whenever(cartItemRepository.findById(cartItemId)).thenReturn(java.util.Optional.of(existingCartItem))

            // Interaction & Assertions
            assertThatThrownBy { cartItemCommandService.updateCartItemQuantity(buyerId, cartItemId, request) }
                .isInstanceOf(CartItemAccessDeniedException::class.java)
        }
    }

    @Nested
    @DisplayName("removeCartItem")
    inner class RemoveCartItem {

        @Test
        fun 장바구니_아이템_삭제_성공() {
            // Data
            val buyerId = 1L
            val cartItemId = 1L
            val existingCartItem = CartItem.fixture(buyerId = buyerId, productId = 100L, quantity = 2).withId(1L)

            // Context
            whenever(cartItemRepository.findById(cartItemId)).thenReturn(java.util.Optional.of(existingCartItem))
            doNothing().`when`(cartItemRepository).deleteById(cartItemId)

            // Interaction
            cartItemCommandService.removeCartItem(buyerId, cartItemId)

            // Assertions
            verify(cartItemRepository).deleteById(cartItemId)
        }

        @Test
        fun 존재하지_않는_장바구니_아이템_삭제_시_예외_발생() {
            // Data
            val buyerId = 1L
            val cartItemId = 999L

            // Context
            whenever(cartItemRepository.findById(cartItemId)).thenReturn(java.util.Optional.empty())

            // Interaction & Assertions
            assertThatThrownBy { cartItemCommandService.removeCartItem(buyerId, cartItemId) }
                .isInstanceOf(CartItemNotFoundException::class.java)
        }

        @Test
        fun 다른_사용자의_장바구니_삭제_시_예외_발생() {
            // Data
            val buyerId = 1L
            val cartItemId = 1L
            val existingCartItem = CartItem.fixture(buyerId = 2L, productId = 100L, quantity = 2).withId(1L)

            // Context
            whenever(cartItemRepository.findById(cartItemId)).thenReturn(java.util.Optional.of(existingCartItem))

            // Interaction & Assertions
            assertThatThrownBy { cartItemCommandService.removeCartItem(buyerId, cartItemId) }
                .isInstanceOf(CartItemAccessDeniedException::class.java)
        }
    }
} 