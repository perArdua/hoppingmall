package com.hoppingmall.order.order.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.hoppingmall.order.cartItem.domain.CartItem
import com.hoppingmall.order.cartItem.domain.repository.CartItemRepository
import com.hoppingmall.order.config.OrderMetrics
import com.hoppingmall.order.order.domain.Order
import com.hoppingmall.order.order.domain.OrderItem
import com.hoppingmall.order.order.domain.repository.OrderItemRepository
import com.hoppingmall.order.order.domain.repository.OrderRepository
import com.hoppingmall.order.order.dto.request.OrderCreateRequest
import com.hoppingmall.order.order.enum.OrderStatus
import com.hoppingmall.order.port.InventoryCommandPort
import com.hoppingmall.order.port.PaymentCommandPort
import com.hoppingmall.order.port.ProductInfo
import com.hoppingmall.order.port.ProductQueryPort
import com.hoppingmall.order.refund.domain.repository.RefundRepository
import com.hoppingmall.outbox.service.TransactionalEventPublisher
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.SliceImpl
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.transaction.support.TransactionCallback
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.Optional

@DisplayName("결제 대기 주문의 자동 취소 시각")
@DisplayNameGeneration(ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class OrderAutoCancelAtTest {

    @Mock
    private lateinit var orderRepository: OrderRepository

    @Mock
    private lateinit var orderItemRepository: OrderItemRepository

    private val objectMapper = ObjectMapper()
        .registerKotlinModule()
        .registerModule(JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)

    @Test
    fun 결제_대기_주문_조회는_마지막_상태_변경에서_결제_대기_시간이_지난_시각을_시간대와_함께_준다() {
        val updatedAt = LocalDateTime.now().minusMinutes(5)
        stubOrder(order(OrderStatus.PAYING, updatedAt))

        val json = toJson(OrderQueryServiceImpl(orderRepository, orderItemRepository).getOrder(ORDER_ID, BUYER_ID))

        val autoCancelAt = OffsetDateTime.parse(json.path("auto_cancel_at").asText())
        assertThat(autoCancelAt.toInstant()).isEqualTo(updatedAt.plusMinutes(15).atZone(ZoneId.systemDefault()).toInstant())
        assertThat(json.path("auto_cancel_at").asText()).matches(".*([+-]\\d{2}:\\d{2}|Z)$")
        assertThat(json.path("auto_cancel_in_seconds").asLong()).isBetween(590L, 600L)
    }

    @Test
    fun 설정한_결제_대기_시간을_따른다() {
        val updatedAt = LocalDateTime.now().minusMinutes(5)
        stubOrder(order(OrderStatus.PAYING, updatedAt))
        val service = OrderQueryServiceImpl(orderRepository, orderItemRepository)
        ReflectionTestUtils.setField(service, "payingTimeoutMinutes", 30L)

        val json = toJson(service.getOrder(ORDER_ID, BUYER_ID))

        assertThat(OffsetDateTime.parse(json.path("auto_cancel_at").asText()).toInstant())
            .isEqualTo(updatedAt.plusMinutes(30).atZone(ZoneId.systemDefault()).toInstant())
    }

    @Test
    fun 시각이_지났으면_남은_초는_0이다() {
        stubOrder(order(OrderStatus.PAYING, LocalDateTime.now().minusMinutes(20)))

        val json = toJson(OrderQueryServiceImpl(orderRepository, orderItemRepository).getOrder(ORDER_ID, BUYER_ID))

        assertThat(json.path("auto_cancel_in_seconds").asLong()).isZero()
        assertThat(json.path("auto_cancel_in_seconds").isNumber).isTrue()
    }

    @Test
    fun 결제_대기가_아닌_주문은_자동_취소_시각이_없다() {
        stubOrder(order(OrderStatus.PAID, LocalDateTime.now().minusMinutes(5)))

        val json = toJson(OrderQueryServiceImpl(orderRepository, orderItemRepository).getOrder(ORDER_ID, BUYER_ID))

        assertThat(json.has("auto_cancel_at")).isTrue()
        assertThat(json.path("auto_cancel_at").isNull).isTrue()
        assertThat(json.path("auto_cancel_in_seconds").isNull).isTrue()
    }

    @Test
    fun 내_주문_목록도_결제_대기_주문에_자동_취소_시각을_준다() {
        val updatedAt = LocalDateTime.now().minusMinutes(1)
        val pageable = PageRequest.of(0, 20)
        whenever(orderRepository.findByBuyerId(BUYER_ID, pageable))
            .thenReturn(SliceImpl(listOf(order(OrderStatus.PAYING, updatedAt)), pageable, false))
        whenever(orderItemRepository.findByOrderIdIn(listOf(ORDER_ID))).thenReturn(listOf(orderItem()))

        val content = OrderQueryServiceImpl(orderRepository, orderItemRepository).getMyOrders(BUYER_ID, pageable).content

        val json = toJson(content.single())
        assertThat(OffsetDateTime.parse(json.path("auto_cancel_at").asText()).toInstant())
            .isEqualTo(updatedAt.plusMinutes(15).atZone(ZoneId.systemDefault()).toInstant())
    }

    @Test
    fun 주문_생성_응답도_자동_취소_시각을_준다() {
        val cartItemRepository = mock<CartItemRepository>()
        val productQueryPort = mock<ProductQueryPort>()
        val inventoryCommandPort = mock<InventoryCommandPort>()
        val transactionTemplate = mock<TransactionTemplate>()
        val service = OrderCommandServiceImpl(
            orderRepository, orderItemRepository, cartItemRepository, productQueryPort, inventoryCommandPort,
            mock<PaymentCommandPort>(), mock<TransactionalEventPublisher>(), mock<OrderMetrics>(), transactionTemplate,
            mock<RefundRepository>()
        )
        val cartItem = CartItem.create(
            buyerId = BUYER_ID, productId = PRODUCT_ID, productName = "상품",
            productPrice = BigDecimal("1000"), productImageUrl = null, quantity = 1
        )
        ReflectionTestUtils.setField(cartItem, "id", CART_ITEM_ID)
        val savedAt = LocalDateTime.now()
        whenever(cartItemRepository.findAllById(listOf(CART_ITEM_ID))).thenReturn(listOf(cartItem))
        whenever(productQueryPort.findProductsByIds(listOf(PRODUCT_ID)))
            .thenReturn(listOf(ProductInfo(id = PRODUCT_ID, name = "상품", price = BigDecimal("1000"), sellerId = SELLER_ID)))
        whenever(inventoryCommandPort.batchReserveStock(any())).thenReturn(mapOf(PRODUCT_ID to "rsv-1"))
        whenever(transactionTemplate.execute(any<TransactionCallback<Any>>())).thenAnswer {
            it.getArgument<TransactionCallback<Any>>(0).doInTransaction(mock())
        }
        whenever(orderRepository.save(any<Order>())).thenAnswer {
            it.getArgument<Order>(0).also { order ->
                ReflectionTestUtils.setField(order, "id", ORDER_ID)
                order.updatedAt = savedAt
            }
        }
        whenever(orderItemRepository.saveAll(any<List<OrderItem>>())).thenAnswer {
            it.getArgument<List<OrderItem>>(0).onEach { item -> ReflectionTestUtils.setField(item, "id", 10L) }
        }

        val json = toJson(service.createOrder(BUYER_ID, OrderCreateRequest(cartItemIds = listOf(CART_ITEM_ID))))

        assertThat(json.path("status").asText()).isEqualTo("PAYING")
        assertThat(OffsetDateTime.parse(json.path("auto_cancel_at").asText()).toInstant())
            .isEqualTo(savedAt.plusMinutes(15).atZone(ZoneId.systemDefault()).toInstant())
    }

    private fun stubOrder(order: Order) {
        whenever(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order))
        whenever(orderItemRepository.findByOrderId(ORDER_ID)).thenReturn(listOf(orderItem()))
    }

    private fun order(status: OrderStatus, updatedAt: LocalDateTime): Order {
        val order = Order.create(buyerId = BUYER_ID, totalAmount = BigDecimal("1000"))
        ReflectionTestUtils.setField(order, "id", ORDER_ID)
        ReflectionTestUtils.setField(order, "status", status)
        order.updatedAt = updatedAt
        return order
    }

    private fun orderItem(): OrderItem {
        val item = OrderItem.create(
            orderId = ORDER_ID, sellerId = SELLER_ID, productId = PRODUCT_ID,
            productName = "상품", productPrice = BigDecimal("1000"), quantity = 1
        )
        ReflectionTestUtils.setField(item, "id", 10L)
        return item
    }

    private fun toJson(value: Any): JsonNode = objectMapper.valueToTree(value)

    companion object {
        private const val ORDER_ID = 1L
        private const val BUYER_ID = 10L
        private const val SELLER_ID = 20L
        private const val PRODUCT_ID = 100L
        private const val CART_ITEM_ID = 5L
    }
}
