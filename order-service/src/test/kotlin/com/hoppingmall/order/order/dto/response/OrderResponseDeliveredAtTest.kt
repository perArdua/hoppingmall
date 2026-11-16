package com.hoppingmall.order.order.dto.response

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.hoppingmall.order.order.domain.Order
import com.hoppingmall.order.order.enum.OrderStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.springframework.test.util.ReflectionTestUtils
import java.math.BigDecimal
import java.time.LocalDateTime

@DisplayName("주문 응답의 배송 완료 시각")
@DisplayNameGeneration(ReplaceUnderscores::class)
class OrderResponseDeliveredAtTest {

    private val objectMapper = ObjectMapper()
        .registerKotlinModule()
        .registerModule(JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)

    @Test
    fun 배송_완료된_주문은_delivered_at에_배송_완료_시각을_준다() {
        val order = order()
        listOf(OrderStatus.PAYING, OrderStatus.PAID, OrderStatus.SHIPPED, OrderStatus.DELIVERED).forEach(order::updateStatus)

        val json = toJson(order)

        assertThat(LocalDateTime.parse(json.path("delivered_at").asText())).isEqualTo(order.deliveredAt)
    }

    @Test
    fun 배송_완료_전_주문은_delivered_at이_null이다() {
        val order = order()
        listOf(OrderStatus.PAYING, OrderStatus.PAID, OrderStatus.SHIPPED).forEach(order::updateStatus)

        val json = toJson(order)

        assertThat(json.has("delivered_at")).isTrue()
        assertThat(json.path("delivered_at").isNull).isTrue()
    }

    private fun order(): Order {
        val order = Order.create(buyerId = 10L, totalAmount = BigDecimal("50000"))
        ReflectionTestUtils.setField(order, "id", 1L)
        return order
    }

    private fun toJson(order: Order): JsonNode = objectMapper.valueToTree(OrderResponse.from(order, emptyList()))
}
