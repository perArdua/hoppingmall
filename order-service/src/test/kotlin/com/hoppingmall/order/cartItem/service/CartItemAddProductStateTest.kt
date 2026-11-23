package com.hoppingmall.order.cartItem.service

import com.hoppingmall.order.cartItem.domain.CartItem
import com.hoppingmall.order.cartItem.domain.repository.CartItemRepository
import com.hoppingmall.order.cartItem.dto.request.CartItemCreateRequest
import com.hoppingmall.order.cartItem.dto.request.CartItemUpdateRequest
import com.hoppingmall.order.cartItem.enum.CartProductStatus
import com.hoppingmall.order.cartItem.exception.CartItemOutOfStockException
import com.hoppingmall.order.cartItem.exception.CartItemProductNotOnSaleException
import com.hoppingmall.order.cartItem.exception.CartItemSelfPurchaseException
import com.hoppingmall.order.port.ProductInfo
import com.hoppingmall.order.port.ProductQueryPort
import com.hoppingmall.order.port.ProductQueryUnavailableException
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
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import org.springframework.test.util.ReflectionTestUtils
import java.math.BigDecimal
import java.util.Optional

@DisplayName("장바구니 담기의 상품 상태·재고 검사")
@DisplayNameGeneration(ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class CartItemAddProductStateTest {

    @Mock
    private lateinit var cartItemRepository: CartItemRepository

    @Mock
    private lateinit var productQueryPort: ProductQueryPort

    @InjectMocks
    private lateinit var service: CartItemCommandServiceImpl

    @Test
    fun 자기_상품은_담지_않고_403_ORD016으로_거절한다() {
        whenever(productQueryPort.findProductById(PRODUCT_ID)).thenReturn(product(sellerId = BUYER_ID))

        assertThatThrownBy { service.addCartItem(BUYER_ID, CartItemCreateRequest(PRODUCT_ID, 1)) }
            .isInstanceOfSatisfying(CartItemSelfPurchaseException::class.java) {
                assertThat(it.errorCode.code).isEqualTo("ORD016")
                assertThat(it.errorCode.status).isEqualTo(HttpStatus.FORBIDDEN)
            }

        verify(cartItemRepository, never()).findByBuyerIdAndProductId(any(), any())
        verify(cartItemRepository, never()).save(any<CartItem>())
    }

    @Test
    fun 품절_상품은_담지_않고_409_CART007로_거절한다() {
        whenever(productQueryPort.findProductById(PRODUCT_ID)).thenReturn(product(status = "SOLD_OUT"))

        assertThatThrownBy { service.addCartItem(BUYER_ID, CartItemCreateRequest(PRODUCT_ID, 1)) }
            .isInstanceOfSatisfying(CartItemProductNotOnSaleException::class.java) {
                assertThat(it.errorCode.code).isEqualTo("CART007")
                assertThat(it.errorCode.status).isEqualTo(HttpStatus.CONFLICT)
            }

        verify(cartItemRepository, never()).save(any<CartItem>())
    }

    @Test
    fun 재고보다_많이_담으면_409_CART008로_거절한다() {
        whenever(productQueryPort.findProductById(PRODUCT_ID)).thenReturn(product(stock = 2))
        whenever(cartItemRepository.findByBuyerIdAndProductId(BUYER_ID, PRODUCT_ID)).thenReturn(null)

        assertThatThrownBy { service.addCartItem(BUYER_ID, CartItemCreateRequest(PRODUCT_ID, 3)) }
            .isInstanceOfSatisfying(CartItemOutOfStockException::class.java) {
                assertThat(it.errorCode.code).isEqualTo("CART008")
                assertThat(it.errorCode.status).isEqualTo(HttpStatus.CONFLICT)
            }

        verify(cartItemRepository, never()).save(any<CartItem>())
    }

    @Test
    fun 다시_담아_합친_수량이_재고를_넘으면_거절하고_수량을_그대로_둔다() {
        val existing = cartItem(quantity = 2)
        whenever(productQueryPort.findProductById(PRODUCT_ID)).thenReturn(product(stock = 3))
        whenever(cartItemRepository.findByBuyerIdAndProductId(BUYER_ID, PRODUCT_ID)).thenReturn(existing)

        assertThatThrownBy { service.addCartItem(BUYER_ID, CartItemCreateRequest(PRODUCT_ID, 2)) }
            .isInstanceOf(CartItemOutOfStockException::class.java)

        assertThat(existing.quantity).isEqualTo(2)
    }

    @Test
    fun 다시_담으면_지금_가격과_재고_상태를_응답에_싣는다() {
        whenever(productQueryPort.findProductById(PRODUCT_ID)).thenReturn(product(price = "12000", stock = 3))
        whenever(cartItemRepository.findByBuyerIdAndProductId(BUYER_ID, PRODUCT_ID)).thenReturn(cartItem(quantity = 1))

        val response = service.addCartItem(BUYER_ID, CartItemCreateRequest(PRODUCT_ID, 2))

        assertThat(response.productPrice).isEqualByComparingTo("12000")
        assertThat(response.totalPrice).isEqualByComparingTo("36000")
        assertThat(response.currentPrice).isEqualByComparingTo("12000")
        assertThat(response.priceChanged).isFalse()
        assertThat(response.inStock).isTrue()
        assertThat(response.productStatus).isEqualTo(CartProductStatus.AVAILABLE)
        assertThat(response.productImageUrl).isEqualTo("http://image")
    }

    @Test
    fun 상품_서비스가_재고를_주지_않으면_재고_검사_없이_담는다() {
        val unknownStock = ProductInfo(id = PRODUCT_ID, name = "상품", price = BigDecimal("10000"), sellerId = 5L)
        whenever(productQueryPort.findProductById(PRODUCT_ID)).thenReturn(unknownStock)
        whenever(cartItemRepository.findByBuyerIdAndProductId(BUYER_ID, PRODUCT_ID)).thenReturn(cartItem(quantity = 1))

        val response = service.addCartItem(BUYER_ID, CartItemCreateRequest(PRODUCT_ID, 1000))

        assertThat(response.quantity).isEqualTo(1001)
    }

    @Test
    fun 수량을_재고보다_많이_바꾸면_거절한다() {
        val existing = cartItem(quantity = 1)
        whenever(cartItemRepository.findById(CART_ITEM_ID)).thenReturn(Optional.of(existing))
        whenever(productQueryPort.findProductById(PRODUCT_ID)).thenReturn(product(stock = 4))

        assertThatThrownBy { service.updateCartItemQuantity(BUYER_ID, CART_ITEM_ID, CartItemUpdateRequest(5)) }
            .isInstanceOf(CartItemOutOfStockException::class.java)

        assertThat(existing.quantity).isEqualTo(1)
    }

    @Test
    fun 수량_변경_응답에_지금_가격과_재고_상태를_싣는다() {
        whenever(cartItemRepository.findById(CART_ITEM_ID)).thenReturn(Optional.of(cartItem(quantity = 1)))
        whenever(productQueryPort.findProductById(PRODUCT_ID)).thenReturn(product(price = "9000", stock = 4))

        val response = service.updateCartItemQuantity(BUYER_ID, CART_ITEM_ID, CartItemUpdateRequest(4))

        assertThat(response.quantity).isEqualTo(4)
        assertThat(response.productPrice).isEqualByComparingTo("10000")
        assertThat(response.currentPrice).isEqualByComparingTo("9000")
        assertThat(response.priceChanged).isTrue()
        assertThat(response.inStock).isTrue()
    }

    @Test
    fun 수량_변경_중_상품이_삭제됐으면_수량은_바꾸고_DELETED로_알린다() {
        whenever(cartItemRepository.findById(CART_ITEM_ID)).thenReturn(Optional.of(cartItem(quantity = 1)))
        whenever(productQueryPort.findProductById(PRODUCT_ID)).thenReturn(null)

        val response = service.updateCartItemQuantity(BUYER_ID, CART_ITEM_ID, CartItemUpdateRequest(2))

        assertThat(response.quantity).isEqualTo(2)
        assertThat(response.productStatus).isEqualTo(CartProductStatus.DELETED)
    }

    @Test
    fun 수량_변경_중_상품_조회가_실패하면_수량은_바꾸고_확인_못_함으로_알린다() {
        whenever(cartItemRepository.findById(CART_ITEM_ID)).thenReturn(Optional.of(cartItem(quantity = 1)))
        whenever(productQueryPort.findProductById(PRODUCT_ID))
            .thenThrow(ProductQueryUnavailableException("상품 조회 실패", RuntimeException()))

        val response = service.updateCartItemQuantity(BUYER_ID, CART_ITEM_ID, CartItemUpdateRequest(2))

        assertThat(response.quantity).isEqualTo(2)
        assertThat(response.productChecked).isFalse()
    }

    private fun product(price: String = "10000", status: String = "AVAILABLE", stock: Int = 100, sellerId: Long = 5L) =
        ProductInfo(
            id = PRODUCT_ID, name = "상품", price = BigDecimal(price), sellerId = sellerId,
            imageUrl = "http://image", status = status, stockQuantity = stock
        )

    private fun cartItem(quantity: Int): CartItem {
        val cartItem = CartItem.create(
            buyerId = BUYER_ID,
            productId = PRODUCT_ID,
            productName = "상품",
            productPrice = BigDecimal("10000"),
            productImageUrl = null,
            quantity = quantity
        )
        ReflectionTestUtils.setField(cartItem, "id", CART_ITEM_ID)
        return cartItem
    }

    companion object {
        private const val BUYER_ID = 1L
        private const val PRODUCT_ID = 100L
        private const val CART_ITEM_ID = 7L
    }
}
