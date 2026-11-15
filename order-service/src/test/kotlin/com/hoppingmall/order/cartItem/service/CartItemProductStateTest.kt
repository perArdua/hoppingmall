package com.hoppingmall.order.cartItem.service

import com.hoppingmall.order.cartItem.domain.CartItem
import com.hoppingmall.order.cartItem.domain.repository.CartItemRepository
import com.hoppingmall.order.cartItem.enum.CartProductStatus
import com.hoppingmall.order.port.ProductInfo
import com.hoppingmall.order.port.ProductQueryPort
import com.hoppingmall.order.port.ProductQueryUnavailableException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.SliceImpl
import org.springframework.test.util.ReflectionTestUtils
import java.math.BigDecimal

@DisplayName("장바구니 목록의 지금 상품 상태")
@DisplayNameGeneration(ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class CartItemProductStateTest {

    @Mock
    private lateinit var cartItemRepository: CartItemRepository

    @Mock
    private lateinit var productQueryPort: ProductQueryPort

    private lateinit var service: CartItemQueryServiceImpl

    private val pageable = PageRequest.of(0, 20)

    @BeforeEach
    fun setUp() {
        service = CartItemQueryServiceImpl(cartItemRepository)
        service.productQueryPort = productQueryPort
    }

    @Test
    fun 상품_조회는_쪽마다_한_번만_묶어_부른다() {
        cart(cartItem(1L, 100L), cartItem(2L, 200L), cartItem(3L, 300L))
        products(product(100L), product(200L), product(300L))

        service.getCartItems(BUYER_ID, pageable)

        verify(productQueryPort, times(1)).findProductsByIds(listOf(100L, 200L, 300L))
        verify(productQueryPort, never()).findProductById(any())
    }

    @Test
    fun 지금_가격이_담을_때와_다르면_지금_가격과_바뀜_표시를_준다() {
        cart(cartItem(1L, 100L, price = "10000"), cartItem(2L, 200L, price = "5000"))
        products(product(100L, price = "12000.00"), product(200L, price = "5000.00"))

        val rows = service.getCartItems(BUYER_ID, pageable).content

        assertThat(rows[0].productPrice).isEqualByComparingTo("10000")
        assertThat(rows[0].currentPrice).isEqualByComparingTo("12000.00")
        assertThat(rows[0].priceChanged).isTrue()
        assertThat(rows[1].priceChanged).isFalse()
        assertThat(rows.map { it.productChecked }).containsOnly(true)
        assertThat(rows.map { it.productStatus }).containsOnly(CartProductStatus.AVAILABLE)
    }

    @Test
    fun 품절_상품_줄은_SOLD_OUT과_재고_없음으로_표시한다() {
        cart(cartItem(1L, 100L))
        products(product(100L, status = "SOLD_OUT", stock = 50))

        val row = service.getCartItems(BUYER_ID, pageable).content.single()

        assertThat(row.productStatus).isEqualTo(CartProductStatus.SOLD_OUT)
        assertThat(row.inStock).isFalse()
    }

    @Test
    fun 재고가_담은_수량보다_적으면_재고_없음으로_표시한다() {
        cart(cartItem(1L, 100L, quantity = 3), cartItem(2L, 200L, quantity = 3))
        products(product(100L, stock = 2), product(200L, stock = 3))

        val rows = service.getCartItems(BUYER_ID, pageable).content

        assertThat(rows.map { it.inStock }).containsExactly(false, true)
    }

    @Test
    fun 삭제된_상품_줄은_빼지_않고_DELETED로_표시한다() {
        cart(cartItem(1L, 100L), cartItem(2L, 200L))
        products(product(200L))

        val rows = service.getCartItems(BUYER_ID, pageable).content

        assertThat(rows.map { it.id }).containsExactly(1L, 2L)
        assertThat(rows[0].productStatus).isEqualTo(CartProductStatus.DELETED)
        assertThat(rows[0].inStock).isFalse()
        assertThat(rows[0].currentPrice).isNull()
        assertThat(rows[0].productChecked).isTrue()
    }

    @Test
    fun 상품_조회가_실패하면_담을_때_값과_확인_못_함_표시로_돌려준다() {
        cart(cartItem(1L, 100L, price = "10000"))
        whenever(productQueryPort.findProductsByIds(any()))
            .thenThrow(ProductQueryUnavailableException("상품 목록 조회 실패", RuntimeException()))

        val row = service.getCartItems(BUYER_ID, pageable).content.single()

        assertThat(row.productPrice).isEqualByComparingTo("10000")
        assertThat(row.productChecked).isFalse()
        assertThat(row.currentPrice).isNull()
        assertThat(row.priceChanged).isFalse()
        assertThat(row.productStatus).isNull()
        assertThat(row.inStock).isNull()
    }

    @Test
    fun 상품_서비스가_상태와_재고를_주지_않으면_두_값을_비운다() {
        cart(cartItem(1L, 100L))
        whenever(productQueryPort.findProductsByIds(any()))
            .thenReturn(listOf(ProductInfo(id = 100L, name = "상품-100", price = BigDecimal("10000"), sellerId = 5L)))

        val row = service.getCartItems(BUYER_ID, pageable).content.single()

        assertThat(row.productChecked).isTrue()
        assertThat(row.productStatus).isNull()
        assertThat(row.inStock).isNull()
    }

    private fun cart(vararg items: CartItem) {
        whenever(cartItemRepository.findByBuyerId(BUYER_ID, pageable)).thenReturn(SliceImpl(items.toList(), pageable, false))
    }

    private fun products(vararg items: ProductInfo) {
        whenever(productQueryPort.findProductsByIds(any())).thenReturn(items.toList())
    }

    private fun product(id: Long, price: String = "10000", status: String = "AVAILABLE", stock: Int = 100) =
        ProductInfo(id = id, name = "상품-$id", price = BigDecimal(price), sellerId = 5L, status = status, stockQuantity = stock)

    private fun cartItem(id: Long, productId: Long, price: String = "10000", quantity: Int = 1): CartItem {
        val cartItem = CartItem.create(
            buyerId = BUYER_ID,
            productId = productId,
            productName = "상품-$productId",
            productPrice = BigDecimal(price),
            productImageUrl = null,
            quantity = quantity
        )
        ReflectionTestUtils.setField(cartItem, "id", id)
        return cartItem
    }

    companion object {
        private const val BUYER_ID = 1L
    }
}
