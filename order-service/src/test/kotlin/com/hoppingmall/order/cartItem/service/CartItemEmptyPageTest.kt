package com.hoppingmall.order.cartItem.service

import com.hoppingmall.order.cartItem.domain.CartItem
import com.hoppingmall.order.cartItem.domain.repository.CartItemRepository
import com.hoppingmall.order.port.ProductInfo
import com.hoppingmall.order.port.ProductQueryPort
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
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.SliceImpl
import org.springframework.test.util.ReflectionTestUtils
import java.math.BigDecimal

@DisplayName("판매 중이 아닌 상품만 담긴 장바구니 쪽")
@DisplayNameGeneration(ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class CartItemEmptyPageTest {

    @Mock
    private lateinit var cartItemRepository: CartItemRepository

    @Mock
    private lateinit var productQueryPort: ProductQueryPort

    private lateinit var service: CartItemQueryServiceImpl

    @BeforeEach
    fun setUp() {
        service = CartItemQueryServiceImpl(cartItemRepository)
        service.productQueryPort = productQueryPort
    }

    @Test
    fun 첫_쪽의_행이_모두_빠지면_빈_쪽_대신_다음_쪽의_판매_중인_행을_돌려준다() {
        pages(listOf(cartItem(1L, STOPPED_PRODUCT_ID)), listOf(cartItem(2L, SELLING_PRODUCT_ID)))
        sellingProductsAre(SELLING_PRODUCT_ID)

        val result = service.getCartItems(BUYER_ID, PageRequest.of(0, 1))

        assertThat(result.content.map { it.id }).containsExactly(2L)
        assertThat(result.number).isEqualTo(1)
        assertThat(result.isLast).isTrue()
    }

    @Test
    fun 남은_행이_모두_판매_중이_아니면_빈_목록과_마지막_쪽으로_답한다() {
        pages(listOf(cartItem(1L, STOPPED_PRODUCT_ID)), listOf(cartItem(2L, OTHER_STOPPED_PRODUCT_ID)))
        sellingProductsAre()

        val result = service.getCartItems(BUYER_ID, PageRequest.of(0, 1))

        assertThat(result.content).isEmpty()
        assertThat(result.isLast).isTrue()
    }

    @Test
    fun 판매_중인_행이_하나라도_있으면_다음_쪽을_읽지_않는다() {
        val first = PageRequest.of(0, 2)
        whenever(cartItemRepository.findByBuyerId(BUYER_ID, first)).thenReturn(
            SliceImpl(listOf(cartItem(1L, STOPPED_PRODUCT_ID), cartItem(2L, SELLING_PRODUCT_ID)), first, true)
        )
        sellingProductsAre(SELLING_PRODUCT_ID)

        val result = service.getCartItems(BUYER_ID, first)

        assertThat(result.content.map { it.id }).containsExactly(2L)
        assertThat(result.number).isEqualTo(0)
        assertThat(result.hasNext()).isTrue()
        verify(cartItemRepository, never()).findByBuyerId(BUYER_ID, first.next())
    }

    private fun pages(vararg contents: List<CartItem>) {
        contents.forEachIndexed { index, content ->
            val pageable = PageRequest.of(index, 1)
            whenever(cartItemRepository.findByBuyerId(BUYER_ID, pageable))
                .thenReturn(SliceImpl(content, pageable, index < contents.lastIndex))
        }
    }

    private fun sellingProductsAre(vararg productIds: Long) {
        whenever(productQueryPort.findProductsByIds(any())).thenAnswer { invocation ->
            invocation.getArgument<List<Long>>(0).filter { it in productIds }
                .map { ProductInfo(id = it, name = "상품-$it", price = BigDecimal("1000"), sellerId = 5L) }
        }
    }

    private fun cartItem(id: Long, productId: Long): CartItem {
        val cartItem = CartItem.create(
            buyerId = BUYER_ID,
            productId = productId,
            productName = "상품-$productId",
            productPrice = BigDecimal("1000"),
            productImageUrl = null,
            quantity = 1
        )
        ReflectionTestUtils.setField(cartItem, "id", id)
        return cartItem
    }

    companion object {
        private const val BUYER_ID = 1L
        private const val STOPPED_PRODUCT_ID = 100L
        private const val OTHER_STOPPED_PRODUCT_ID = 101L
        private const val SELLING_PRODUCT_ID = 200L
    }
}
