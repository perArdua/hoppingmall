package com.hoppingmall.order.order.service

import com.hoppingmall.order.cartItem.domain.CartItem
import com.hoppingmall.order.order.dto.request.OrderCreateRequest
import com.hoppingmall.order.port.ProductInfo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.test.util.ReflectionTestUtils
import java.math.BigDecimal

@DisplayName("주문 금액의 상품 가격 기준 MySQL 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class OrderCreateCurrentPriceMySqlIntegrationTest : OrderCreateFenceScenarios() {

    @Test
    fun 담은_뒤_판매자가_가격을_올리면_주문은_올린_가격으로_매긴다() {
        stubCart(cartPrice = "10000.00", quantity = 3)
        stubProduct(price = "15000.00", name = "지금 이름")

        val order = orderCommandService.createOrder(BUYER_ID, OrderCreateRequest(cartItemIds = listOf(CART_ITEM_ID)))

        assertThat(order.totalAmount).isEqualByComparingTo("45000.00")
        assertThat(order.items.single().productPrice).isEqualByComparingTo("15000.00")
        assertThat(order.items.single().totalPrice).isEqualByComparingTo("45000.00")
        assertThat(order.items.single().productName).isEqualTo("지금 이름")
        assertThat(storedOrderTotal()).isEqualByComparingTo("45000.00")
        assertThat(storedOrderItem()).containsEntry("name", "지금 이름")
        assertThat(storedOrderItem()["price"] as BigDecimal).isEqualByComparingTo("15000.00")
    }

    @Test
    fun 담은_뒤_판매자가_가격을_내리면_주문은_내린_가격으로_매긴다() {
        stubCart(cartPrice = "10000.00", quantity = 2)
        stubProduct(price = "7000.00", name = "지금 이름")

        val order = orderCommandService.createOrder(BUYER_ID, OrderCreateRequest(cartItemIds = listOf(CART_ITEM_ID)))

        assertThat(order.totalAmount).isEqualByComparingTo("14000.00")
        assertThat(order.items.single().productPrice).isEqualByComparingTo("7000.00")
        assertThat(storedOrderTotal()).isEqualByComparingTo("14000.00")
        assertThat(storedOrderItem()["price"] as BigDecimal).isEqualByComparingTo("7000.00")
    }

    private fun stubCart(cartPrice: String, quantity: Int) {
        val cartItem = CartItem.create(
            buyerId = BUYER_ID,
            productId = PRODUCT_ID,
            productName = "담을 때 이름",
            productPrice = BigDecimal(cartPrice),
            productImageUrl = null,
            quantity = quantity
        )
        ReflectionTestUtils.setField(cartItem, "id", CART_ITEM_ID)
        whenever(cartItemRepository.findAllById(listOf(CART_ITEM_ID))).thenReturn(listOf(cartItem))
        whenever(inventoryCommandPort.batchReserveStock(any())).thenReturn(mapOf(PRODUCT_ID to FIRST_RESERVATION))
    }

    private fun stubProduct(price: String, name: String) {
        whenever(productQueryPort.findProductsByIds(listOf(PRODUCT_ID)))
            .thenReturn(listOf(ProductInfo(id = PRODUCT_ID, name = name, price = BigDecimal(price), sellerId = 5L)))
    }

    private fun storedOrderTotal(): BigDecimal =
        jdbcTemplate.queryForObject("SELECT total_amount FROM orders", BigDecimal::class.java)!!

    private fun storedOrderItem(): Map<String, Any> =
        jdbcTemplate.queryForMap("SELECT product_price AS price, product_name AS name FROM order_items")

    companion object {
        private const val CART_ITEM_ID = 1L
        private const val PRODUCT_ID = 100L
    }
}
