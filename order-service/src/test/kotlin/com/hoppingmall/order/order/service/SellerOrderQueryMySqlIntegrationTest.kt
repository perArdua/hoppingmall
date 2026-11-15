package com.hoppingmall.order.order.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.hoppingmall.order.order.domain.Order
import com.hoppingmall.order.order.domain.OrderItem
import com.hoppingmall.order.order.domain.OrderShippingAddress
import com.hoppingmall.order.order.domain.repository.OrderItemRepository
import com.hoppingmall.order.order.domain.repository.OrderRepository
import com.hoppingmall.order.order.enum.OrderStatus
import com.hoppingmall.order.order.exception.OrderAccessDeniedException
import com.hoppingmall.order.order.exception.OrderNotFoundException
import com.hoppingmall.order.support.MySqlJpaTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import java.math.BigDecimal

@DisplayName("판매자 주문 목록·상세 MySQL 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class SellerOrderQueryMySqlIntegrationTest : MySqlJpaTestSupport() {

    @Autowired
    private lateinit var orderRepository: OrderRepository

    @Autowired
    private lateinit var orderItemRepository: OrderItemRepository

    private val objectMapper = ObjectMapper()
        .registerKotlinModule()
        .registerModule(JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)

    private val service by lazy { OrderQueryServiceImpl(orderRepository, orderItemRepository) }

    @Test
    fun 목록은_자기_상품이_든_결제_뒤_주문만_최신순으로_주고_항목은_자기_것만_준다() {
        val mixed = saveOrder(OrderStatus.PAID, SELLER_ID to "3000", OTHER_SELLER_ID to "5000", SELLER_ID to "2000")
        val own = saveOrder(OrderStatus.SHIPPED, SELLER_ID to "1000")
        saveOrder(OrderStatus.PAID, OTHER_SELLER_ID to "7000")
        saveOrder(OrderStatus.PAYING, SELLER_ID to "1000")
        saveOrder(OrderStatus.CREATED, SELLER_ID to "1000")
        val cancelled = saveOrder(OrderStatus.CANCELLED, SELLER_ID to "1000")

        val page = service.getSellerOrders(SELLER_ID, null, PageRequest.of(0, 20))

        assertThat(page.content.map { it.id }).containsExactly(cancelled, own, mixed)
        val mixedJson = json(page.content.last())
        assertThat(mixedJson.path("items").map { it.path("seller_id").asLong() }).containsOnly(SELLER_ID)
        assertThat(mixedJson.path("items").map { it.path("total_price").decimalValue() })
            .usingElementComparator(BigDecimal::compareTo)
            .containsExactly(BigDecimal("3000"), BigDecimal("2000"))
        assertThat(mixedJson.path("seller_total_amount").decimalValue()).isEqualByComparingTo("5000")
        assertThat(mixedJson.has("buyer_id")).isFalse()
        assertThat(mixedJson.has("total_amount")).isFalse()
        assertThat(mixedJson.path("shipping_address").isNull).isTrue()
    }

    @Test
    fun 상태로_거르면_그_상태의_주문만_주고_결제_전_상태는_비어_있다() {
        val paid = saveOrder(OrderStatus.PAID, SELLER_ID to "1000")
        saveOrder(OrderStatus.SHIPPED, SELLER_ID to "1000")
        saveOrder(OrderStatus.PAYING, SELLER_ID to "1000")

        assertThat(service.getSellerOrders(SELLER_ID, OrderStatus.PAID, PageRequest.of(0, 20)).content.map { it.id })
            .containsExactly(paid)
        assertThat(service.getSellerOrders(SELLER_ID, OrderStatus.PAYING, PageRequest.of(0, 20)).content).isEmpty()
        assertThat(service.getSellerOrders(SELLER_ID, OrderStatus.CREATED, PageRequest.of(0, 20)).content).isEmpty()
    }

    @Test
    fun 쪽으로_나눠_주고_요청의_정렬은_무시하고_최신순을_지킨다() {
        val ids = (1..3).map { saveOrder(OrderStatus.PAID, SELLER_ID to "1000") }

        val first = service.getSellerOrders(SELLER_ID, null, PageRequest.of(0, 2, Sort.by("id").ascending()))
        val second = service.getSellerOrders(SELLER_ID, null, PageRequest.of(1, 2))

        assertThat(first.content.map { it.id }).containsExactly(ids[2], ids[1])
        assertThat(first.hasNext()).isTrue()
        assertThat(second.content.map { it.id }).containsExactly(ids[0])
        assertThat(second.hasNext()).isFalse()
    }

    @Test
    fun 상세는_배송할_주문에만_받는_사람과_주소를_주고_구매자_id와_남의_항목은_주지_않는다() {
        val paid = saveOrder(OrderStatus.PAID, SELLER_ID to "3000", OTHER_SELLER_ID to "5000", address = address())
        val shipped = saveOrder(OrderStatus.SHIPPED, SELLER_ID to "3000", address = address())

        listOf(paid, shipped).forEach { orderId ->
            val detail = json(service.getSellerOrder(orderId, SELLER_ID))
            assertThat(detail.path("items").map { it.path("seller_id").asLong() }).containsOnly(SELLER_ID)
            assertThat(detail.path("seller_total_amount").decimalValue()).isEqualByComparingTo("3000")
            assertThat(detail.has("buyer_id")).isFalse()
            val shipping = detail.path("shipping_address")
            assertThat(shipping.path("recipient_name").asText()).isEqualTo("홍길동")
            assertThat(shipping.path("recipient_phone").asText()).isEqualTo("010-1234-5678")
            assertThat(shipping.path("zip_code").asText()).isEqualTo("06236")
            assertThat(shipping.path("address").asText()).isEqualTo("서울시 강남구 테헤란로 1")
            assertThat(shipping.path("delivery_memo").asText()).isEqualTo("문 앞")
        }
    }

    @Test
    fun 상세는_배송이_끝났거나_취소된_주문에_받는_사람과_주소를_주지_않는다() {
        listOf(OrderStatus.DELIVERED, OrderStatus.CANCELLED, OrderStatus.CANCEL_REQUESTED, OrderStatus.REFUNDED).forEach { status ->
            val orderId = saveOrder(status, SELLER_ID to "1000", address = address())

            val detail = json(service.getSellerOrder(orderId, SELLER_ID))

            assertThat(detail.path("status").asText()).isEqualTo(status.name)
            assertThat(detail.path("shipping_address").isNull).describedAs("status=%s", status).isTrue()
        }
    }

    @Test
    fun 상세는_자기_상품이_없으면_403_결제_전이거나_없으면_404다() {
        val others = saveOrder(OrderStatus.PAID, OTHER_SELLER_ID to "1000")
        val paying = saveOrder(OrderStatus.PAYING, SELLER_ID to "1000")

        assertThatThrownBy { service.getSellerOrder(others, SELLER_ID) }.isInstanceOf(OrderAccessDeniedException::class.java)
        assertThatThrownBy { service.getSellerOrder(paying, SELLER_ID) }.isInstanceOf(OrderNotFoundException::class.java)
        assertThatThrownBy { service.getSellerOrder(999_999L, SELLER_ID) }.isInstanceOf(OrderNotFoundException::class.java)
    }

    private fun json(value: Any): JsonNode = objectMapper.valueToTree(value)

    private fun address() = OrderShippingAddress(
        recipientName = "홍길동",
        recipientPhone = "010-1234-5678",
        zipCode = "06236",
        address = "서울시 강남구 테헤란로 1",
        addressDetail = "101호",
        deliveryMemo = "문 앞"
    )

    private fun saveOrder(status: OrderStatus, vararg items: Pair<Long, String>, address: OrderShippingAddress? = null): Long {
        val total = items.fold(BigDecimal.ZERO) { acc, (_, price) -> acc + BigDecimal(price) }
        val order = Order.create(buyerId = BUYER_ID, totalAmount = total, shippingAddress = address)
        PATHS.getValue(status).forEach { order.updateStatus(it) }
        if (status == OrderStatus.REFUNDED) order.completeRefund()
        val orderId = orderRepository.save(order).id!!
        items.forEachIndexed { index, (sellerId, price) ->
            orderItemRepository.save(
                OrderItem.create(
                    orderId = orderId,
                    sellerId = sellerId,
                    productId = 100L + index,
                    productName = "상품$index",
                    productPrice = BigDecimal(price),
                    quantity = 1
                )
            )
        }
        return orderId
    }

    companion object {
        private const val BUYER_ID = 10L
        private const val SELLER_ID = 20L
        private const val OTHER_SELLER_ID = 30L

        private val PATHS = mapOf(
            OrderStatus.CREATED to emptyList(),
            OrderStatus.PAYING to listOf(OrderStatus.PAYING),
            OrderStatus.PAID to listOf(OrderStatus.PAYING, OrderStatus.PAID),
            OrderStatus.SHIPPED to listOf(OrderStatus.PAYING, OrderStatus.PAID, OrderStatus.SHIPPED),
            OrderStatus.DELIVERED to listOf(OrderStatus.PAYING, OrderStatus.PAID, OrderStatus.SHIPPED, OrderStatus.DELIVERED),
            OrderStatus.REFUNDED to listOf(OrderStatus.PAYING, OrderStatus.PAID, OrderStatus.SHIPPED, OrderStatus.DELIVERED),
            OrderStatus.CANCELLED to listOf(OrderStatus.PAYING, OrderStatus.CANCELLED),
            OrderStatus.CANCEL_REQUESTED to listOf(OrderStatus.PAYING, OrderStatus.PAID, OrderStatus.CANCEL_REQUESTED)
        )
    }
}
