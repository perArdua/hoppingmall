package com.hoppingmall.order.order.service

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.hoppingmall.order.cartItem.domain.CartItem
import com.hoppingmall.order.cartItem.domain.repository.CartItemRepository
import com.hoppingmall.order.config.OrderMetrics
import com.hoppingmall.order.order.domain.Order
import com.hoppingmall.order.order.domain.OrderItem
import com.hoppingmall.order.order.domain.repository.OrderItemRepository
import com.hoppingmall.order.order.domain.repository.OrderRepository
import com.hoppingmall.order.order.dto.request.OrderCreateRequest
import com.hoppingmall.order.order.dto.request.ShippingAddressRequest
import com.hoppingmall.order.port.InventoryCommandPort
import com.hoppingmall.order.port.PaymentCommandPort
import com.hoppingmall.order.port.ProductInfo
import com.hoppingmall.order.port.ProductQueryPort
import com.hoppingmall.order.refund.domain.repository.RefundRepository
import com.hoppingmall.outbox.service.TransactionalEventPublisher
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.slf4j.LoggerFactory
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.transaction.support.TransactionCallback
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal

@DisplayName("주문 생성 때 배송지를 주문에 남김")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class OrderCreateShippingAddressTest {

    private val orderRepository: OrderRepository = mock()
    private val orderItemRepository: OrderItemRepository = mock()
    private val cartItemRepository: CartItemRepository = mock()
    private val productQueryPort: ProductQueryPort = mock()
    private val inventoryCommandPort: InventoryCommandPort = mock()
    private val transactionTemplate: TransactionTemplate = mock()

    private val service = OrderCommandServiceImpl(
        orderRepository, orderItemRepository, cartItemRepository,
        productQueryPort, inventoryCommandPort, mock<PaymentCommandPort>(),
        mock<TransactionalEventPublisher>(), mock<OrderMetrics>(), transactionTemplate, mock<RefundRepository>()
    )

    private val logger = LoggerFactory.getLogger(OrderCommandServiceImpl::class.java) as Logger
    private val appender = ListAppender<ILoggingEvent>()

    @BeforeEach
    fun setUp() {
        appender.start()
        logger.addAppender(appender)
        whenever(transactionTemplate.execute(any<TransactionCallback<Any>>())).thenAnswer {
            it.getArgument<TransactionCallback<Any>>(0).doInTransaction(mock())
        }
        val cartItem = CartItem.create(
            buyerId = BUYER_ID, productId = 100L, productName = "상품", productPrice = BigDecimal("25000"),
            productImageUrl = null, quantity = 1
        )
        ReflectionTestUtils.setField(cartItem, "id", 1L)
        whenever(cartItemRepository.findAllById(listOf(1L))).thenReturn(listOf(cartItem))
        whenever(productQueryPort.findProductsByIds(listOf(100L)))
            .thenReturn(listOf(ProductInfo(id = 100L, name = "상품", price = BigDecimal("25000"), sellerId = 5L)))
        whenever(inventoryCommandPort.batchReserveStock(listOf(100L to 1))).thenReturn(mapOf(100L to "rsv-1"))
        whenever(orderRepository.save(any<Order>())).thenAnswer {
            val order = it.arguments[0] as Order
            ReflectionTestUtils.setField(order, "id", ORDER_ID)
            order
        }
        whenever(orderItemRepository.saveAll(any<List<OrderItem>>())).thenAnswer {
            @Suppress("UNCHECKED_CAST")
            val items = it.arguments[0] as List<OrderItem>
            items.forEachIndexed { index, item -> ReflectionTestUtils.setField(item, "id", index + 1L) }
            items
        }
    }

    @AfterEach
    fun tearDown() {
        logger.detachAppender(appender)
        appender.stop()
    }

    @Test
    fun 주문을_만들면_요청의_배송지를_주문에_남기고_응답에_돌려준다() {
        val result = service.createOrder(BUYER_ID, OrderCreateRequest(cartItemIds = listOf(1L), shippingAddress = address()))

        val saved = argumentCaptor<Order>().apply { verify(orderRepository).save(capture()) }.firstValue
        val stored = saved.shippingAddress!!
        assertThat(stored.recipientName).isEqualTo("홍길동")
        assertThat(stored.recipientPhone).isEqualTo("010-1234-5678")
        assertThat(stored.zipCode).isEqualTo("06236")
        assertThat(stored.address).isEqualTo("서울시 강남구 테헤란로 1")
        assertThat(stored.addressDetail).isEqualTo("101동 202호")
        assertThat(stored.deliveryMemo).isEqualTo("문 앞에 놓아 주세요")
        val response = result.shippingAddress!!
        assertThat(response.recipientName).isEqualTo("홍길동")
        assertThat(response.recipientPhone).isEqualTo("010-1234-5678")
        assertThat(response.zipCode).isEqualTo("06236")
        assertThat(response.addressDetail).isEqualTo("101동 202호")
        assertThat(response.deliveryMemo).isEqualTo("문 앞에 놓아 주세요")
    }

    @Test
    fun 주문_생성_로그에는_배송지_원문이_없다() {
        service.createOrder(BUYER_ID, OrderCreateRequest(cartItemIds = listOf(1L), shippingAddress = address()))

        val messages = appender.list.map { it.formattedMessage }
        assertThat(messages).anyMatch { it.startsWith("주문 생성") }
        assertThat(messages.joinToString("\n")).doesNotContain("홍길동", "010-1234-5678", "06236", "테헤란로", "101동", "문 앞")
    }

    private fun address() = ShippingAddressRequest(
        recipientName = "홍길동",
        recipientPhone = "010-1234-5678",
        zipCode = "06236",
        address = "서울시 강남구 테헤란로 1",
        addressDetail = "101동 202호",
        deliveryMemo = "문 앞에 놓아 주세요"
    )

    companion object {
        private const val BUYER_ID = 10L
        private const val ORDER_ID = 1L
    }
}
