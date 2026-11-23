package com.hoppingmall.order.order.dto.request

import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.validation.Valid
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull

data class OrderCreateRequest(
    @field:NotEmpty(message = "장바구니 아이템 ID 목록은 비어있을 수 없습니다")
    val cartItemIds: List<Long>,

    @field:NotNull(message = "배송지는 필수입니다.")
    @field:Valid
    val shippingAddress: ShippingAddressRequest? = null,

    @field:NotEmpty(message = "장바구니 아이템마다 화면에 보인 가격이 필요합니다")
    @field:Valid
    val expectedPrices: List<OrderExpectedPriceRequest> = emptyList()
) {
    @get:JsonIgnore
    @get:AssertTrue(message = "기대 가격은 주문할 장바구니 아이템마다 하나씩 있어야 합니다")
    val expectedPricesMatchCartItems: Boolean
        get() {
            if (expectedPrices.isEmpty()) return true
            val expectedIds = expectedPrices.map { it.cartItemId }
            return expectedIds.size == expectedIds.toSet().size && expectedIds.toSet() == cartItemIds.toSet()
        }
}
