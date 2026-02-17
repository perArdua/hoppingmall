package com.hoppingmall.mall.refund.service

import com.hoppingmall.mall.inventory.service.InventoryCommandService
import com.hoppingmall.mall.order.domain.Order
import com.hoppingmall.mall.order.domain.OrderItem
import com.hoppingmall.mall.order.domain.repository.OrderItemRepository
import com.hoppingmall.mall.order.domain.repository.OrderRepository
import com.hoppingmall.mall.order.enum.OrderStatus
import com.hoppingmall.mall.order.exception.OrderNotFoundException
import com.hoppingmall.mall.payment.domain.Payment
import com.hoppingmall.mall.payment.domain.repository.PaymentRepository
import com.hoppingmall.mall.payment.exception.PaymentNotFoundException
import com.hoppingmall.mall.point.service.PointCommandService
import com.hoppingmall.mall.product.domain.Product
import com.hoppingmall.mall.product.domain.repository.ProductRepository
import com.hoppingmall.mall.product.service.ProductStatisticsCommandService
import com.hoppingmall.mall.refund.domain.Refund
import com.hoppingmall.mall.refund.domain.RefundItem
import com.hoppingmall.mall.refund.domain.repository.RefundItemRepository
import com.hoppingmall.mall.refund.domain.repository.RefundRepository
import com.hoppingmall.mall.refund.dto.request.RefundApprovalRequest
import com.hoppingmall.mall.refund.dto.request.RefundCreateRequest
import com.hoppingmall.mall.refund.dto.request.RefundItemRequest
import com.hoppingmall.mall.refund.enum.RefundReason
import com.hoppingmall.mall.refund.enum.RefundStatus
import com.hoppingmall.mall.refund.exception.RefundAccessDeniedException
import com.hoppingmall.mall.refund.exception.RefundAlreadyExistsException
import com.hoppingmall.mall.refund.exception.RefundException
import com.hoppingmall.mall.refund.exception.RefundNotFoundException
import com.hoppingmall.mall.shipping.domain.Shipping
import com.hoppingmall.mall.shipping.domain.repository.ShippingRepository
import com.hoppingmall.mall.support.fixture.*
import com.hoppingmall.mall.support.withId
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.math.BigDecimal
import java.util.*

@DisplayName("RefundCommandServiceImpl")
@DisplayNameGeneration(ReplaceUnderscores::class)
class RefundCommandServiceImplTest {

    private val refundRepository: RefundRepository = mock()
    private val refundItemRepository: RefundItemRepository = mock()
    private val orderRepository: OrderRepository = mock()
    private val orderItemRepository: OrderItemRepository = mock()
    private val paymentRepository: PaymentRepository = mock()
    private val productRepository: ProductRepository = mock()
    private val shippingRepository: ShippingRepository = mock()
    private val inventoryCommandService: InventoryCommandService = mock()
    private val pointCommandService: PointCommandService = mock()
    private val productStatisticsCommandService: ProductStatisticsCommandService = mock()

    private val refundCommandService = RefundCommandServiceImpl(
        refundRepository,
        refundItemRepository,
        orderRepository,
        orderItemRepository,
        paymentRepository,
        productRepository,
        shippingRepository,
        inventoryCommandService,
        pointCommandService,
        productStatisticsCommandService
    )

    @Nested
    @DisplayName("requestRefund")
    inner class RequestRefund {

        @Test
        fun `배송_전_전체_환불_요청_시_자동_승인_및_완료`() {
            // given
            val buyerId = 1L
            val orderId = 1L
            val order = Order.paidFixture(buyerId = buyerId)
            val payment = Payment.successFixture(orderId = orderId, pointAmount = BigDecimal("1000"))
            val orderItem = OrderItem.fixture(orderId = orderId, productId = 100L, quantity = 2)
            val product = Product.fixture(sellerId = 2L).withId(100L)

            val request = RefundCreateRequest(
                orderId = orderId,
                reason = RefundReason.CHANGE_OF_MIND,
                reasonDetail = null,
                items = listOf(RefundItemRequest(orderItemId = 1L, quantity = 2))
            )

            whenever(orderRepository.findById(orderId)).thenReturn(Optional.of(order))
            whenever(paymentRepository.findByOrderId(orderId)).thenReturn(payment)
            whenever(refundRepository.findByOrderIdAndStatusNot(orderId, RefundStatus.REJECTED)).thenReturn(emptyList())
            whenever(orderItemRepository.findByOrderId(orderId)).thenReturn(listOf(orderItem))
            whenever(productRepository.findById(100L)).thenReturn(Optional.of(product))
            whenever(shippingRepository.findByOrderId(orderId)).thenReturn(null)
            whenever(refundRepository.save(any<Refund>())).thenAnswer { invocation ->
                (invocation.arguments[0] as Refund).withId(1L)
            }
            whenever(refundItemRepository.saveAll(any<List<RefundItem>>())).thenAnswer { invocation ->
                @Suppress("UNCHECKED_CAST")
                (invocation.arguments[0] as List<RefundItem>).mapIndexed { index, item -> item.withId(index.toLong() + 1) }
            }

            // when
            val response = refundCommandService.requestRefund(buyerId, request)

            // then
            assertEquals(RefundStatus.COMPLETED, response.status)
            assertTrue(response.isFullRefund)
            verify(inventoryCommandService).increaseStock(100L, 2)
            verify(pointCommandService).refundPoints(eq(buyerId), eq(BigDecimal("1000")), eq(1L), eq(orderId))
            verify(productStatisticsCommandService).incrementRefundStats(eq(100L), eq(2L), any())
        }

        @Test
        fun `배송_중_환불_요청_시_REQUESTED_상태로_생성`() {
            // given
            val buyerId = 1L
            val orderId = 1L
            val order = Order.fixture(buyerId = buyerId, status = OrderStatus.SHIPPED)
            val payment = Payment.successFixture(orderId = orderId)
            val orderItem = OrderItem.fixture(orderId = orderId, productId = 100L, quantity = 2)
            val product = Product.fixture(sellerId = 2L).withId(100L)
            val shipping = Shipping.inTransitFixture(orderId = orderId)

            val request = RefundCreateRequest(
                orderId = orderId,
                reason = RefundReason.DEFECTIVE_PRODUCT,
                reasonDetail = "상품 파손",
                items = listOf(RefundItemRequest(orderItemId = 1L, quantity = 2))
            )

            whenever(orderRepository.findById(orderId)).thenReturn(Optional.of(order))
            whenever(paymentRepository.findByOrderId(orderId)).thenReturn(payment)
            whenever(refundRepository.findByOrderIdAndStatusNot(orderId, RefundStatus.REJECTED)).thenReturn(emptyList())
            whenever(orderItemRepository.findByOrderId(orderId)).thenReturn(listOf(orderItem))
            whenever(productRepository.findById(100L)).thenReturn(Optional.of(product))
            whenever(shippingRepository.findByOrderId(orderId)).thenReturn(shipping)
            whenever(refundRepository.save(any<Refund>())).thenAnswer { invocation ->
                (invocation.arguments[0] as Refund).withId(1L)
            }
            whenever(refundItemRepository.saveAll(any<List<RefundItem>>())).thenAnswer { invocation ->
                @Suppress("UNCHECKED_CAST")
                (invocation.arguments[0] as List<RefundItem>).mapIndexed { index, item -> item.withId(index.toLong() + 1) }
            }

            // when
            val response = refundCommandService.requestRefund(buyerId, request)

            // then
            assertEquals(RefundStatus.REQUESTED, response.status)
            verify(inventoryCommandService, never()).increaseStock(any(), any())
            verify(pointCommandService, never()).refundPoints(any(), any(), any(), any())
        }

        @Test
        fun `부분_환불_요청_성공`() {
            // given
            val buyerId = 1L
            val orderId = 1L
            val order = Order.paidFixture(buyerId = buyerId)
            val payment = Payment.successFixture(orderId = orderId, amount = BigDecimal("50000"), pointAmount = BigDecimal("1000"))
            val orderItem1 = OrderItem.fixture(orderId = orderId, productId = 100L, productPrice = BigDecimal("15000"), quantity = 2)
            val orderItem2 = OrderItem.fixture(orderId = orderId, productId = 200L, productPrice = BigDecimal("20000"), quantity = 1).withId(2L)
            val product = Product.fixture(sellerId = 2L).withId(100L)

            val request = RefundCreateRequest(
                orderId = orderId,
                reason = RefundReason.CHANGE_OF_MIND,
                items = listOf(RefundItemRequest(orderItemId = 1L, quantity = 1))
            )

            whenever(orderRepository.findById(orderId)).thenReturn(Optional.of(order))
            whenever(paymentRepository.findByOrderId(orderId)).thenReturn(payment)
            whenever(refundRepository.findByOrderIdAndStatusNot(orderId, RefundStatus.REJECTED)).thenReturn(emptyList())
            whenever(orderItemRepository.findByOrderId(orderId)).thenReturn(listOf(orderItem1, orderItem2))
            whenever(productRepository.findById(100L)).thenReturn(Optional.of(product))
            whenever(shippingRepository.findByOrderId(orderId)).thenReturn(null)
            whenever(refundRepository.save(any<Refund>())).thenAnswer { invocation ->
                (invocation.arguments[0] as Refund).withId(1L)
            }
            whenever(refundItemRepository.saveAll(any<List<RefundItem>>())).thenAnswer { invocation ->
                @Suppress("UNCHECKED_CAST")
                (invocation.arguments[0] as List<RefundItem>).mapIndexed { index, item -> item.withId(index.toLong() + 1) }
            }

            // when
            val response = refundCommandService.requestRefund(buyerId, request)

            // then
            assertFalse(response.isFullRefund)
            assertEquals(BigDecimal("15000"), response.refundAmount)
            verify(inventoryCommandService).increaseStock(100L, 1)
        }

        @Test
        fun `다른_사용자의_주문에_환불_요청_시_예외_발생`() {
            // given
            val buyerId = 1L
            val order = Order.paidFixture(buyerId = 999L)

            val request = RefundCreateRequest(
                orderId = 1L,
                reason = RefundReason.CHANGE_OF_MIND,
                items = listOf(RefundItemRequest(orderItemId = 1L, quantity = 1))
            )

            whenever(orderRepository.findById(1L)).thenReturn(Optional.of(order))

            // when & then
            assertThrows(RefundAccessDeniedException::class.java) {
                refundCommandService.requestRefund(buyerId, request)
            }
        }

        @Test
        fun `존재하지_않는_주문에_환불_요청_시_예외_발생`() {
            // given
            val request = RefundCreateRequest(
                orderId = 999L,
                reason = RefundReason.CHANGE_OF_MIND,
                items = listOf(RefundItemRequest(orderItemId = 1L, quantity = 1))
            )

            whenever(orderRepository.findById(999L)).thenReturn(Optional.empty())

            // when & then
            assertThrows(OrderNotFoundException::class.java) {
                refundCommandService.requestRefund(1L, request)
            }
        }

        @Test
        fun `CREATED_상태_주문에_환불_요청_시_예외_발생`() {
            // given
            val buyerId = 1L
            val order = Order.fixture(buyerId = buyerId, status = OrderStatus.CREATED)

            val request = RefundCreateRequest(
                orderId = 1L,
                reason = RefundReason.CHANGE_OF_MIND,
                items = listOf(RefundItemRequest(orderItemId = 1L, quantity = 1))
            )

            whenever(orderRepository.findById(1L)).thenReturn(Optional.of(order))

            // when & then
            assertThrows(RefundException::class.java) {
                refundCommandService.requestRefund(buyerId, request)
            }
        }

        @Test
        fun `결제가_성공_상태가_아닌_경우_예외_발생`() {
            // given
            val buyerId = 1L
            val order = Order.paidFixture(buyerId = buyerId)
            val payment = Payment.fixture(orderId = 1L, status = com.hoppingmall.mall.payment.enum.PaymentStatus.FAILED)

            val request = RefundCreateRequest(
                orderId = 1L,
                reason = RefundReason.CHANGE_OF_MIND,
                items = listOf(RefundItemRequest(orderItemId = 1L, quantity = 1))
            )

            whenever(orderRepository.findById(1L)).thenReturn(Optional.of(order))
            whenever(paymentRepository.findByOrderId(1L)).thenReturn(payment)

            // when & then
            assertThrows(RefundException::class.java) {
                refundCommandService.requestRefund(buyerId, request)
            }
        }

        @Test
        fun `이미_진행_중인_환불이_있으면_예외_발생`() {
            // given
            val buyerId = 1L
            val order = Order.paidFixture(buyerId = buyerId)
            val payment = Payment.successFixture(orderId = 1L)
            val existingRefund = Refund.fixture(status = RefundStatus.REQUESTED)

            val request = RefundCreateRequest(
                orderId = 1L,
                reason = RefundReason.CHANGE_OF_MIND,
                items = listOf(RefundItemRequest(orderItemId = 1L, quantity = 1))
            )

            whenever(orderRepository.findById(1L)).thenReturn(Optional.of(order))
            whenever(paymentRepository.findByOrderId(1L)).thenReturn(payment)
            whenever(refundRepository.findByOrderIdAndStatusNot(1L, RefundStatus.REJECTED)).thenReturn(listOf(existingRefund))

            // when & then
            assertThrows(RefundAlreadyExistsException::class.java) {
                refundCommandService.requestRefund(buyerId, request)
            }
        }

        @Test
        fun `환불_수량이_주문_수량을_초과하면_예외_발생`() {
            // given
            val buyerId = 1L
            val order = Order.paidFixture(buyerId = buyerId)
            val payment = Payment.successFixture(orderId = 1L)
            val orderItem = OrderItem.fixture(orderId = 1L, quantity = 2)

            val request = RefundCreateRequest(
                orderId = 1L,
                reason = RefundReason.CHANGE_OF_MIND,
                items = listOf(RefundItemRequest(orderItemId = 1L, quantity = 5))
            )

            whenever(orderRepository.findById(1L)).thenReturn(Optional.of(order))
            whenever(paymentRepository.findByOrderId(1L)).thenReturn(payment)
            whenever(refundRepository.findByOrderIdAndStatusNot(1L, RefundStatus.REJECTED)).thenReturn(emptyList())
            whenever(orderItemRepository.findByOrderId(1L)).thenReturn(listOf(orderItem))

            // when & then
            assertThrows(RefundException::class.java) {
                refundCommandService.requestRefund(buyerId, request)
            }
        }

        @Test
        fun `결제_정보가_없으면_예외_발생`() {
            // given
            val buyerId = 1L
            val order = Order.paidFixture(buyerId = buyerId)

            val request = RefundCreateRequest(
                orderId = 1L,
                reason = RefundReason.CHANGE_OF_MIND,
                items = listOf(RefundItemRequest(orderItemId = 1L, quantity = 1))
            )

            whenever(orderRepository.findById(1L)).thenReturn(Optional.of(order))
            whenever(paymentRepository.findByOrderId(1L)).thenReturn(null)

            // when & then
            assertThrows(PaymentNotFoundException::class.java) {
                refundCommandService.requestRefund(buyerId, request)
            }
        }
    }

    @Nested
    @DisplayName("approveRefund")
    inner class ApproveRefund {

        @Test
        fun `판매자가_환불_승인_성공`() {
            // given
            val refundId = 1L
            val sellerId = 2L
            val refund = Refund.fixture(sellerId = sellerId, status = RefundStatus.REQUESTED)
            val refundItem = RefundItem.fixture(refundId = refundId, productId = 100L, quantity = 2)
            val payment = Payment.successFixture(pointAmount = BigDecimal("1000"))
            val order = Order.paidFixture()

            whenever(refundRepository.findById(refundId)).thenReturn(Optional.of(refund))
            whenever(refundItemRepository.findByRefundId(refundId)).thenReturn(listOf(refundItem))
            whenever(paymentRepository.findById(refund.paymentId)).thenReturn(Optional.of(payment))
            whenever(orderRepository.findById(refund.orderId)).thenReturn(Optional.of(order))
            whenever(refundRepository.save(any<Refund>())).thenAnswer { it.arguments[0] }

            // when
            val response = refundCommandService.approveRefund(refundId, sellerId)

            // then
            assertEquals(RefundStatus.COMPLETED, response.status)
            verify(inventoryCommandService).increaseStock(100L, 2)
            verify(pointCommandService).refundPoints(eq(1L), any(), eq(1L), eq(1L))
            verify(productStatisticsCommandService).incrementRefundStats(eq(100L), eq(2L), any())
        }

        @Test
        fun `다른_판매자가_승인_시도_시_예외_발생`() {
            // given
            val refundId = 1L
            val refund = Refund.fixture(sellerId = 2L, status = RefundStatus.REQUESTED)

            whenever(refundRepository.findById(refundId)).thenReturn(Optional.of(refund))

            // when & then
            assertThrows(RefundAccessDeniedException::class.java) {
                refundCommandService.approveRefund(refundId, 999L)
            }
        }

        @Test
        fun `존재하지_않는_환불_승인_시_예외_발생`() {
            // given
            whenever(refundRepository.findById(999L)).thenReturn(Optional.empty())

            // when & then
            assertThrows(RefundNotFoundException::class.java) {
                refundCommandService.approveRefund(999L, 2L)
            }
        }
    }

    @Nested
    @DisplayName("rejectRefund")
    inner class RejectRefund {

        @Test
        fun `판매자가_환불_거절_성공`() {
            // given
            val refundId = 1L
            val sellerId = 2L
            val refund = Refund.fixture(sellerId = sellerId, status = RefundStatus.REQUESTED)
            val refundItem = RefundItem.fixture(refundId = refundId)
            val rejectionRequest = RefundApprovalRequest(rejectionReason = "반품 불가 상품")

            whenever(refundRepository.findById(refundId)).thenReturn(Optional.of(refund))
            whenever(refundItemRepository.findByRefundId(refundId)).thenReturn(listOf(refundItem))
            whenever(refundRepository.save(any<Refund>())).thenAnswer { it.arguments[0] }

            // when
            val response = refundCommandService.rejectRefund(refundId, sellerId, rejectionRequest)

            // then
            assertEquals(RefundStatus.REJECTED, response.status)
            assertEquals("반품 불가 상품", response.rejectionReason)
            verify(inventoryCommandService, never()).increaseStock(any(), any())
        }

        @Test
        fun `다른_판매자가_거절_시도_시_예외_발생`() {
            // given
            val refundId = 1L
            val refund = Refund.fixture(sellerId = 2L, status = RefundStatus.REQUESTED)
            val rejectionRequest = RefundApprovalRequest(rejectionReason = "거절")

            whenever(refundRepository.findById(refundId)).thenReturn(Optional.of(refund))

            // when & then
            assertThrows(RefundAccessDeniedException::class.java) {
                refundCommandService.rejectRefund(refundId, 999L, rejectionRequest)
            }
        }
    }
}
