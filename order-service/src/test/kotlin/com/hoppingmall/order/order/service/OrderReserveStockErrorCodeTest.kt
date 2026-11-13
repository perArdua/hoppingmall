package com.hoppingmall.order.order.service

import com.hoppingmall.order.cartItem.domain.CartItem
import com.hoppingmall.order.cartItem.domain.repository.CartItemRepository
import com.hoppingmall.order.config.OrderMetrics
import com.hoppingmall.order.order.domain.Order
import com.hoppingmall.order.order.domain.repository.OrderItemRepository
import com.hoppingmall.order.order.domain.repository.OrderRepository
import com.hoppingmall.order.order.dto.request.OrderCreateRequest
import com.hoppingmall.order.order.exception.OrderOutOfStockException
import com.hoppingmall.order.port.InventoryCommandPort
import com.hoppingmall.order.port.PaymentCommandPort
import com.hoppingmall.order.port.ProductInfo
import com.hoppingmall.order.port.ProductQueryPort
import com.hoppingmall.order.refund.domain.repository.RefundRepository
import com.hoppingmall.outbox.service.TransactionalEventPublisher
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.client.HttpClientErrorException
import java.math.BigDecimal
import java.nio.charset.StandardCharsets

@DisplayName("주문 생성의 재고 배치 예약 400 응답 처리")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class OrderReserveStockErrorCodeTest {

    @Mock
    private lateinit var orderRepository: OrderRepository

    @Mock
    private lateinit var orderItemRepository: OrderItemRepository

    @Mock
    private lateinit var cartItemRepository: CartItemRepository

    @Mock
    private lateinit var productQueryPort: ProductQueryPort

    @Mock
    private lateinit var inventoryCommandPort: InventoryCommandPort

    @Mock
    private lateinit var paymentCommandPort: PaymentCommandPort

    @Mock
    private lateinit var transactionalEventPublisher: TransactionalEventPublisher

    @Mock
    private lateinit var orderMetrics: OrderMetrics

    @Mock
    private lateinit var transactionTemplate: TransactionTemplate

    @Mock
    private lateinit var refundRepository: RefundRepository

    private lateinit var service: OrderCommandServiceImpl

    @BeforeEach
    fun setUp() {
        service = OrderCommandServiceImpl(
            orderRepository, orderItemRepository, cartItemRepository,
            productQueryPort, inventoryCommandPort, paymentCommandPort,
            transactionalEventPublisher, orderMetrics, transactionTemplate, refundRepository
        )
        val cartItem = CartItem.create(
            buyerId = 10L,
            productId = 100L,
            productName = "상품-100",
            productPrice = BigDecimal("25000"),
            productImageUrl = null,
            quantity = 1
        )
        whenever(cartItemRepository.findAllById(listOf(1L))).thenReturn(listOf(cartItem))
        whenever(productQueryPort.findProductsByIds(listOf(100L))).thenReturn(
            listOf(ProductInfo(id = 100L, name = "상품-100", price = BigDecimal("25000"), sellerId = 5L))
        )
    }

    @Test
    fun 재고_부족_INV002는_ORD007_재고_부족으로_바꾼다() {
        whenever(inventoryCommandPort.batchReserveStock(any())).thenThrow(badRequest("INV002", "재고가 부족합니다."))

        assertThatThrownBy { service.createOrder(10L, OrderCreateRequest(cartItemIds = listOf(1L))) }
            .isInstanceOf(OrderOutOfStockException::class.java)
        verify(orderRepository, never()).save(any<Order>())
    }

    @Test
    fun 예약_수량_오류_INV007은_재고_부족으로_바꾸지_않는다() {
        whenever(inventoryCommandPort.batchReserveStock(any())).thenThrow(badRequest("INV007", "예약 수량은 1개 이상이어야 합니다."))

        assertThatThrownBy { service.createOrder(10L, OrderCreateRequest(cartItemIds = listOf(1L))) }
            .isInstanceOf(HttpClientErrorException.BadRequest::class.java)
            .isNotInstanceOf(OrderOutOfStockException::class.java)
            .satisfies({ assertThat((it as HttpClientErrorException).responseBodyAsString).contains("INV007") })
        verify(orderRepository, never()).save(any<Order>())
    }

    @Test
    fun 예약_요청_키_오류_INV010은_재고_부족으로_바꾸지_않는다() {
        whenever(inventoryCommandPort.batchReserveStock(any())).thenThrow(badRequest("INV010", "예약 요청 키 오류"))

        assertThatThrownBy { service.createOrder(10L, OrderCreateRequest(cartItemIds = listOf(1L))) }
            .isInstanceOf(HttpClientErrorException.BadRequest::class.java)
            .isNotInstanceOf(OrderOutOfStockException::class.java)
    }

    @Test
    fun 요청_본문_오류_C001은_재고_부족으로_바꾸지_않는다() {
        whenever(inventoryCommandPort.batchReserveStock(any())).thenThrow(badRequest("C001", "잘못된 입력입니다."))

        assertThatThrownBy { service.createOrder(10L, OrderCreateRequest(cartItemIds = listOf(1L))) }
            .isInstanceOf(HttpClientErrorException.BadRequest::class.java)
            .isNotInstanceOf(OrderOutOfStockException::class.java)
    }

    @Test
    fun 오류_코드를_읽을_수_없는_400은_재고_부족으로_바꾸지_않는다() {
        val unreadable = HttpClientErrorException.create(
            HttpStatus.BAD_REQUEST, "Bad Request", HttpHeaders.EMPTY,
            "<html>bad request</html>".toByteArray(StandardCharsets.UTF_8), StandardCharsets.UTF_8
        )
        whenever(inventoryCommandPort.batchReserveStock(any())).thenThrow(unreadable)

        assertThatThrownBy { service.createOrder(10L, OrderCreateRequest(cartItemIds = listOf(1L))) }
            .isInstanceOf(HttpClientErrorException.BadRequest::class.java)
            .isNotInstanceOf(OrderOutOfStockException::class.java)
    }

    private fun badRequest(code: String, message: String): HttpClientErrorException {
        val body = """{"code":"$code","message":"$message","data":null}"""
        return HttpClientErrorException.create(
            HttpStatus.BAD_REQUEST, "Bad Request", HttpHeaders.EMPTY,
            body.toByteArray(StandardCharsets.UTF_8), StandardCharsets.UTF_8
        )
    }
}
