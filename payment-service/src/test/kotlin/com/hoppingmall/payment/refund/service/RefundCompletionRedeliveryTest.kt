package com.hoppingmall.payment.refund.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.hoppingmall.payment.port.InventoryCommandPort
import com.hoppingmall.payment.port.OrderCommandPort
import com.hoppingmall.payment.port.ProductStatisticsPort
import com.hoppingmall.payment.refund.domain.RefundEventLog
import com.hoppingmall.payment.refund.domain.repository.RefundEventLogRepository
import com.hoppingmall.payment.refund.dto.event.RefundCompletedEvent
import com.hoppingmall.payment.refund.dto.event.RefundItemEvent
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.stubbing.Stubber
import java.math.BigDecimal
import java.util.concurrent.atomic.AtomicBoolean

@DisplayName("환불 완료 이벤트가 다시 전달될 때 원격 작업은 항목마다 한 번만")
@DisplayNameGeneration(ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class RefundCompletionRedeliveryTest {

    @Mock
    private lateinit var refundEventLogRepository: RefundEventLogRepository

    @Mock
    private lateinit var refundLocalOperationService: RefundLocalOperationService

    @Mock
    private lateinit var inventoryCommandPort: InventoryCommandPort

    @Mock
    private lateinit var orderCommandPort: OrderCommandPort

    @Mock
    private lateinit var productStatisticsPort: ProductStatisticsPort

    @Mock
    private lateinit var objectMapper: ObjectMapper

    @InjectMocks
    private lateinit var consumer: RefundCompletionConsumer

    private fun refundEvent(isFullRefund: Boolean) = RefundCompletedEvent(
        eventId = "refund-completed-747",
        refundId = 747L,
        orderId = 100L,
        paymentId = 200L,
        buyerId = 300L,
        refundAmount = BigDecimal("47000"),
        pointRefundAmount = BigDecimal.ZERO,
        isFullRefund = isFullRefund,
        couponId = if (isFullRefund) 77L else null,
        items = listOf(
            RefundItemEvent(productId = 101L, quantity = 1, refundPrice = BigDecimal("15000")),
            RefundItemEvent(productId = 102L, quantity = 2, refundPrice = BigDecimal("32000"))
        )
    )

    private fun storedLog(event: RefundCompletedEvent): RefundEventLog {
        val log = RefundEventLog(
            eventId = event.eventId,
            eventType = "REFUND_COMPLETED",
            refundId = event.refundId,
            orderId = event.orderId
        )
        whenever(refundEventLogRepository.findByEventIdWithSteps(event.eventId)).thenReturn(null, log)
        whenever(refundEventLogRepository.save(any<RefundEventLog>())).thenReturn(log)
        return log
    }

    private fun failOnceFor(productId: Long): Stubber {
        val failed = AtomicBoolean(false)
        return doAnswer {
            if (it.arguments[0] == productId && failed.compareAndSet(false, true)) {
                throw IllegalStateException("product 일시 장애")
            }
        }
    }

    @Test
    fun 두_번째_항목의_재고_증가가_한_번_실패한_뒤_같은_이벤트가_다시_오면_재고_증가와_통계는_항목마다_한_번이다() {
        val event = refundEvent(isFullRefund = true)
        storedLog(event)
        failOnceFor(102L).whenever(inventoryCommandPort).increaseStock(any(), any())

        assertThatThrownBy { consumer.processRefundCompletion(event) }
            .isInstanceOf(IllegalStateException::class.java)
        consumer.processRefundCompletion(event)

        verify(inventoryCommandPort, times(1)).increaseStock(101L, 1)
        verify(inventoryCommandPort, times(2)).increaseStock(102L, 2)
        verify(productStatisticsPort, times(1)).incrementRefundStats(101L, 1L, BigDecimal("15000"))
        verify(productStatisticsPort, times(1)).incrementRefundStats(102L, 2L, BigDecimal("32000"))
        verify(orderCommandPort, times(1)).cancelOrder(100L)
    }

    @Test
    fun 부분_환불에서_두_번째_항목의_통계가_한_번_실패한_뒤_같은_이벤트가_다시_오면_통계와_재고_증가는_항목마다_한_번이다() {
        val event = refundEvent(isFullRefund = false)
        storedLog(event)
        failOnceFor(102L).whenever(productStatisticsPort).incrementRefundStats(any(), any(), any())

        assertThatThrownBy { consumer.processRefundCompletion(event) }
            .isInstanceOf(IllegalStateException::class.java)
        consumer.processRefundCompletion(event)

        verify(productStatisticsPort, times(1)).incrementRefundStats(101L, 1L, BigDecimal("15000"))
        verify(productStatisticsPort, times(2)).incrementRefundStats(102L, 2L, BigDecimal("32000"))
        verify(inventoryCommandPort, times(1)).increaseStock(101L, 1)
        verify(inventoryCommandPort, times(1)).increaseStock(102L, 2)
    }

    @Test
    fun 처리가_끝난_같은_이벤트가_다시_오면_재고_증가와_통계와_주문_취소를_다시_부르지_않는다() {
        val event = refundEvent(isFullRefund = true)
        storedLog(event)

        consumer.processRefundCompletion(event)
        consumer.processRefundCompletion(event)

        verify(inventoryCommandPort, times(1)).increaseStock(101L, 1)
        verify(inventoryCommandPort, times(1)).increaseStock(102L, 2)
        verify(productStatisticsPort, times(1)).incrementRefundStats(101L, 1L, BigDecimal("15000"))
        verify(productStatisticsPort, times(1)).incrementRefundStats(102L, 2L, BigDecimal("32000"))
        verify(orderCommandPort, times(1)).cancelOrder(100L)
    }
}
