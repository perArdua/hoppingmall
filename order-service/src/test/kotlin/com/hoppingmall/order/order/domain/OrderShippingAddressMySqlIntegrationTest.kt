package com.hoppingmall.order.order.domain

import com.hoppingmall.order.order.domain.repository.OrderRepository
import com.hoppingmall.order.order.enum.OrderStatus
import com.hoppingmall.order.support.MySqlJpaTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.math.BigDecimal

@DisplayName("주문 배송지 열 MySQL 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class OrderShippingAddressMySqlIntegrationTest : MySqlJpaTestSupport() {

    @Autowired
    private lateinit var orderRepository: OrderRepository

    @Test
    fun 주문의_배송지가_주문_행의_배송지_열에_저장되고_다시_읽힌다() {
        val orderId = orderRepository.save(Order.create(buyerId = 1L, totalAmount = BigDecimal("50000"), shippingAddress = address())).id!!

        val row = jdbcTemplate.queryForMap(
            """
            SELECT shipping_recipient_name, shipping_recipient_phone, shipping_zip_code,
                   shipping_address, shipping_address_detail, shipping_delivery_memo
            FROM orders WHERE id = ?
            """.trimIndent(),
            orderId
        )
        assertThat(row["shipping_recipient_name"]).isEqualTo("홍길동")
        assertThat(row["shipping_recipient_phone"]).isEqualTo("010-1234-5678")
        assertThat(row["shipping_zip_code"]).isEqualTo("06236")
        assertThat(row["shipping_address"]).isEqualTo("서울시 강남구 테헤란로 1")
        assertThat(row["shipping_address_detail"]).isEqualTo("101동 202호")
        assertThat(row["shipping_delivery_memo"]).isEqualTo("문 앞")

        val loaded = orderRepository.findById(orderId).get().shippingAddress!!
        assertThat(loaded.recipientName).isEqualTo("홍길동")
        assertThat(loaded.addressDetail).isEqualTo("101동 202호")
    }

    @Test
    fun 주문_상태가_바뀌어도_주문의_배송지는_그대로다() {
        val order = orderRepository.save(Order.create(buyerId = 1L, totalAmount = BigDecimal("50000"), shippingAddress = address()))
        order.updateStatus(OrderStatus.PAYING)
        order.updateStatus(OrderStatus.PAID)
        orderRepository.save(order)

        val loaded = orderRepository.findById(order.id!!).get()
        assertThat(loaded.status).isEqualTo(OrderStatus.PAID)
        assertThat(loaded.shippingAddress!!.recipientPhone).isEqualTo("010-1234-5678")
        assertThat(loaded.shippingAddress!!.zipCode).isEqualTo("06236")
    }

    @Test
    fun 배송지_없이_만든_주문은_배송지_열이_비고_배송지가_없다() {
        val orderId = orderRepository.save(Order.create(buyerId = 1L, totalAmount = BigDecimal("50000"))).id!!

        val nonNullColumns = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) FROM orders WHERE id = ? AND (
              shipping_recipient_name IS NOT NULL OR shipping_recipient_phone IS NOT NULL OR shipping_zip_code IS NOT NULL
              OR shipping_address IS NOT NULL OR shipping_address_detail IS NOT NULL OR shipping_delivery_memo IS NOT NULL)
            """.trimIndent(),
            Long::class.java,
            orderId
        )
        assertThat(nonNullColumns).isZero()
        assertThat(orderRepository.findById(orderId).get().shippingAddress).isNull()
    }

    @Test
    fun 배송지_열은_모두_비워_둘_수_있어_기존_주문_행을_그대로_읽는다() {
        val nullable = jdbcTemplate.queryForList(
            """
            SELECT COLUMN_NAME, IS_NULLABLE FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'orders' AND COLUMN_NAME LIKE 'shipping\_%'
            """.trimIndent()
        ).associate { it["COLUMN_NAME"] to it["IS_NULLABLE"] }

        assertThat(nullable).hasSize(6)
        assertThat(nullable.values).containsOnly("YES")
    }

    private fun address() = OrderShippingAddress(
        recipientName = "홍길동",
        recipientPhone = "010-1234-5678",
        zipCode = "06236",
        address = "서울시 강남구 테헤란로 1",
        addressDetail = "101동 202호",
        deliveryMemo = "문 앞"
    )
}
