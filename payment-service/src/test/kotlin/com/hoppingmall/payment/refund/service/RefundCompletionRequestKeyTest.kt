package com.hoppingmall.payment.refund.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.hoppingmall.payment.port.HttpInventoryCommandAdapter
import com.hoppingmall.payment.port.HttpProductStatisticsAdapter
import com.hoppingmall.payment.port.OrderCommandPort
import com.hoppingmall.payment.refund.domain.RefundEventLog
import com.hoppingmall.payment.refund.domain.repository.RefundEventLogRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.hamcrest.Matchers.startsWith
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.whenever
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.client.ClientHttpRequest
import org.springframework.http.client.ClientHttpResponse
import org.springframework.mock.http.client.MockClientHttpResponse
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.test.web.client.ExpectedCount
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.ResponseCreator
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.web.client.RestTemplate
import java.util.concurrent.atomic.AtomicBoolean

@DisplayName("환불 완료 처리가 재고 증가·환불 통계에 요청 키를 넘겨 다시 처리돼도 product는 한 번만 반영")
@DisplayNameGeneration(ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class RefundCompletionRequestKeyTest {

    @Mock
    private lateinit var refundEventLogRepository: RefundEventLogRepository

    @Mock
    private lateinit var refundLocalOperationService: RefundLocalOperationService

    @Mock
    private lateinit var orderCommandPort: OrderCommandPort

    private val restTemplate = RestTemplate()
    private val server = MockRestServiceServer.bindTo(restTemplate).ignoreExpectOrder(true).build()
    private val product = FakeProductServer()
    private lateinit var consumer: RefundCompletionConsumer

    @BeforeEach
    fun setUp() {
        val statisticsAdapter = HttpProductStatisticsAdapter(PRODUCT_URL, RestTemplateBuilder())
        ReflectionTestUtils.setField(statisticsAdapter, "restTemplate", restTemplate)
        consumer = RefundCompletionConsumer(
            refundEventLogRepository,
            refundLocalOperationService,
            HttpInventoryCommandAdapter(PRODUCT_URL, restTemplate),
            orderCommandPort,
            statisticsAdapter,
            ObjectMapper()
        )
        server.expect(ExpectedCount.manyTimes(), requestTo(startsWith("$PRODUCT_URL/internal/api/v1/")))
            .andExpect(method(HttpMethod.POST))
            .andRespond(product)

        val log = RefundEventLog(eventId = EVENT_ID, eventType = "REFUND_COMPLETED", refundId = 747L, orderId = 100L)
        whenever(refundEventLogRepository.findByEventIdWithSteps(EVENT_ID)).thenReturn(null, log)
        whenever(refundEventLogRepository.save(any<RefundEventLog>())).thenReturn(log)
    }

    private fun failOnceWhenMarking(step: String) {
        val failed = AtomicBoolean(false)
        doAnswer {
            if (it.arguments[1] == step && failed.compareAndSet(false, true)) {
                throw IllegalStateException("완료 기록 저장 실패")
            }
        }.whenever(refundLocalOperationService).markStepAndSave(any(), any())
    }

    private fun message(vararg items: String): String = """
        {"eventType":"RefundCompleted","eventData":{"eventId":"$EVENT_ID","refundId":747,"orderId":100,"paymentId":200,
        "buyerId":300,"refundAmount":"15000","pointRefundAmount":"0","isFullRefund":false,"couponId":null,
        "items":[${items.joinToString(",")}]}}
    """.trimIndent()

    private fun item(productId: Long, orderItemId: Long?): String {
        val orderItem = orderItemId?.let { ""","orderItemId":$it""" } ?: ""
        return """{"productId":$productId,"quantity":1,"refundPrice":"15000"$orderItem}"""
    }

    @Test
    fun 재고_증가가_성공한_뒤_완료_기록이_실패해_다시_처리돼도_같은_요청_키라_재고는_한_번만_늘어난다() {
        failOnceWhenMarking(RefundEventLog.inventoryItemStep(0))

        assertThatThrownBy { consumer.handleRefundCompletionEvent(message(item(101L, 9001L))) }
            .isInstanceOf(IllegalStateException::class.java)
        consumer.handleRefundCompletionEvent(message(item(101L, 9001L)))

        assertThat(product.keysFor(INVENTORY_101)).containsExactly("refund-747-item-9001", "refund-747-item-9001")
        assertThat(product.appliedCount(INVENTORY_101)).isEqualTo(1)
        assertThat(product.keysFor(STATS_101)).containsExactly("refund-747-item-9001-stat")
        assertThat(product.appliedCount(STATS_101)).isEqualTo(1)
    }

    @Test
    fun 환불_통계가_성공한_뒤_완료_기록이_실패해_다시_처리돼도_같은_요청_키라_통계는_한_번만_반영된다() {
        failOnceWhenMarking(RefundEventLog.statsItemStep(0))

        assertThatThrownBy { consumer.handleRefundCompletionEvent(message(item(101L, 9001L))) }
            .isInstanceOf(IllegalStateException::class.java)
        consumer.handleRefundCompletionEvent(message(item(101L, 9001L)))

        assertThat(product.keysFor(STATS_101)).containsExactly("refund-747-item-9001-stat", "refund-747-item-9001-stat")
        assertThat(product.appliedCount(STATS_101)).isEqualTo(1)
        assertThat(product.keysFor(INVENTORY_101)).containsExactly("refund-747-item-9001")
        assertThat(product.appliedCount(INVENTORY_101)).isEqualTo(1)
    }

    @Test
    fun 같은_상품의_주문_항목이_둘이면_항목마다_다른_요청_키로_각각_반영된다() {
        consumer.handleRefundCompletionEvent(message(item(101L, 9001L), item(101L, 9002L)))

        assertThat(product.keysFor(INVENTORY_101)).containsExactly("refund-747-item-9001", "refund-747-item-9002")
        assertThat(product.appliedCount(INVENTORY_101)).isEqualTo(2)
        assertThat(product.keysFor(STATS_101)).containsExactly("refund-747-item-9001-stat", "refund-747-item-9002-stat")
        assertThat(product.appliedCount(STATS_101)).isEqualTo(2)
    }

    @Test
    fun 주문_항목_id가_없는_예전_이벤트는_요청_키_없이_보낸다() {
        consumer.handleRefundCompletionEvent(message(item(101L, null)))

        assertThat(product.keysFor(INVENTORY_101)).containsExactly(null)
        assertThat(product.keysFor(STATS_101)).containsExactly(null)
    }

    private class FakeProductServer : ResponseCreator {
        private val requests = mutableListOf<Pair<String, String?>>()
        private val appliedKeys = mutableSetOf<Pair<String, String>>()
        private val applied = mutableMapOf<String, Int>()

        override fun createResponse(request: ClientHttpRequest?): ClientHttpResponse {
            val path = request!!.uri.path
            val key = request.headers.getFirst("X-Request-Key")
            requests += path to key
            if (key == null || appliedKeys.add(path to key)) {
                applied.merge(path, 1, Int::plus)
            }
            return MockClientHttpResponse(ByteArray(0), HttpStatus.OK)
        }

        fun keysFor(path: String): List<String?> = requests.filter { it.first == path }.map { it.second }

        fun appliedCount(path: String): Int = applied[path] ?: 0
    }

    companion object {
        private const val PRODUCT_URL = "http://localhost:8083"
        private const val EVENT_ID = "refund-completed-812"
        private const val INVENTORY_101 = "/internal/api/v1/inventory/101/increase"
        private const val STATS_101 = "/internal/api/v1/product-statistics/101/refund"
    }
}
