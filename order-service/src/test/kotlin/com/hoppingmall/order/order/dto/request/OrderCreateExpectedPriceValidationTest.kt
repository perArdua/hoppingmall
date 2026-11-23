package com.hoppingmall.order.order.dto.request

import jakarta.validation.Validation
import jakarta.validation.Validator
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import java.math.BigDecimal

@DisplayName("주문 생성 요청의 기대 가격 검증")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class OrderCreateExpectedPriceValidationTest {

    private val validator: Validator = Validation.buildDefaultValidatorFactory().validator

    @Test
    fun 기대_가격이_없으면_검증에_실패한다() {
        assertThat(paths(request(listOf(1L, 2L)))).containsExactly("expectedPrices")
    }

    @Test
    fun 장바구니_아이템마다_기대_가격이_하나씩_있으면_통과한다() {
        assertThat(paths(request(listOf(1L, 2L), expected(2L, "500"), expected(1L, "10000.00")))).isEmpty()
    }

    @Test
    fun 기대_가격이_빠진_장바구니_아이템이_있으면_실패한다() {
        assertThat(paths(request(listOf(1L, 2L), expected(1L, "10000")))).containsExactly("expectedPricesMatchCartItems")
    }

    @Test
    fun 주문하지_않는_장바구니_아이템의_기대_가격이_있으면_실패한다() {
        assertThat(paths(request(listOf(1L), expected(1L, "10000"), expected(3L, "10000")))).containsExactly("expectedPricesMatchCartItems")
    }

    @Test
    fun 같은_장바구니_아이템의_기대_가격이_두_번_오면_실패한다() {
        assertThat(paths(request(listOf(1L), expected(1L, "10000"), expected(1L, "9000")))).containsExactly("expectedPricesMatchCartItems")
    }

    @Test
    fun 기대_가격이_음수면_실패한다() {
        assertThat(paths(request(listOf(1L), expected(1L, "-1")))).containsExactly("expectedPrices[0].productPrice")
    }

    private fun paths(request: OrderCreateRequest): List<String> =
        validator.validate(request).map { it.propertyPath.toString() }

    private fun request(cartItemIds: List<Long>, vararg expectedPrices: OrderExpectedPriceRequest) =
        OrderCreateRequest(
            cartItemIds = cartItemIds,
            shippingAddress = ShippingAddressRequest(
                recipientName = "구매자", recipientPhone = "010-1234-5678", zipCode = "06236",
                address = "서울시 강남구 테헤란로 1", addressDetail = null, deliveryMemo = null
            ),
            expectedPrices = expectedPrices.toList()
        )

    private fun expected(cartItemId: Long, price: String) = OrderExpectedPriceRequest(cartItemId, BigDecimal(price))
}
