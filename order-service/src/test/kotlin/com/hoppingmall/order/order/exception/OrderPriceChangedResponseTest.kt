package com.hoppingmall.order.order.exception

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.hoppingmall.order.order.dto.response.OrderPriceChange
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import java.math.BigDecimal

@DisplayName("주문 가격 바뀜 응답 검증")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class OrderPriceChangedResponseTest {

    private val handler = OrderPriceChangedExceptionHandler()

    private val objectMapper = ObjectMapper().registerKotlinModule()
        .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)

    @Test
    fun 가격_바뀜은_409와_ORD014로_항목별_화면_가격과_지금_가격을_돌려준다() {
        val change = OrderPriceChange(cartItemId = 7L, productId = 100L, expectedPrice = BigDecimal("10000.00"), currentPrice = BigDecimal("12000.00"))

        val response = handler.handleOrderPriceChanged(OrderPriceChangedException(listOf(change)))

        assertThat(response.statusCode).isEqualTo(HttpStatus.CONFLICT)
        assertThat(response.body?.code).isEqualTo("ORD014")
        assertThat(response.body?.data?.items).containsExactly(change)
        val json = objectMapper.readTree(objectMapper.writeValueAsString(response.body))
        val item = json.path("data").path("items").path(0)
        assertThat(item.path("cart_item_id").asLong()).isEqualTo(7L)
        assertThat(item.path("product_id").asLong()).isEqualTo(100L)
        assertThat(item.path("expected_price").decimalValue()).isEqualByComparingTo("10000.00")
        assertThat(item.path("current_price").decimalValue()).isEqualByComparingTo("12000.00")
    }
}
