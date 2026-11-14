package com.hoppingmall.payment.refund.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.hoppingmall.payment.port.HttpProductStatisticsAdapter
import com.hoppingmall.payment.port.InventoryCommandPort
import com.hoppingmall.payment.port.OrderCommandPort
import com.hoppingmall.payment.refund.domain.RefundEventLog
import com.hoppingmall.payment.refund.domain.repository.RefundEventLogRepository
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.startsWith
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration
import org.springframework.boot.autoconfigure.web.client.RestTemplateAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpStatus
import org.springframework.mock.http.client.MockClientHttpRequest
import org.springframework.mock.http.client.MockClientHttpResponse
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.test.web.client.ExpectedCount
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.web.client.RestTemplate

@DisplayName("환불 완료 처리가 product 환불 통계 요청 본문에 정가를 넘김")
@DisplayNameGeneration(ReplaceUnderscores::class)
@SpringBootTest(
    classes = [HttpProductStatisticsAdapter::class],
    properties = ["services.product-service.url=http://product.test"]
)
@ImportAutoConfiguration(
    JacksonAutoConfiguration::class,
    HttpMessageConvertersAutoConfiguration::class,
    RestTemplateAutoConfiguration::class
)
class RefundStatsProductPriceTest {

    @Autowired
    private lateinit var statisticsAdapter: HttpProductStatisticsAdapter

    private val refundEventLogRepository: RefundEventLogRepository = mock()
    private val refundLocalOperationService: RefundLocalOperationService = mock()
    private val inventoryCommandPort: InventoryCommandPort = mock()
    private val orderCommandPort: OrderCommandPort = mock()
    private val objectMapper = ObjectMapper()
    private val statsRequests = mutableListOf<Pair<String, String>>()
    private lateinit var consumer: RefundCompletionConsumer

    @BeforeEach
    fun setUp() {
        val restTemplate = ReflectionTestUtils.getField(statisticsAdapter, "restTemplate") as RestTemplate
        val server = MockRestServiceServer.bindTo(restTemplate).build()
        server.expect(ExpectedCount.manyTimes(), requestTo(startsWith("http://product.test/internal/api/v1/product-statistics/")))
            .andRespond { request ->
                statsRequests += request!!.uri.toString() to (request as MockClientHttpRequest).bodyAsString
                MockClientHttpResponse(ByteArray(0), HttpStatus.OK)
            }
        consumer = RefundCompletionConsumer(
            refundEventLogRepository,
            refundLocalOperationService,
            inventoryCommandPort,
            orderCommandPort,
            statisticsAdapter,
            objectMapper
        )
        val log = RefundEventLog(eventId = EVENT_ID, eventType = "REFUND_COMPLETED", refundId = 738L, orderId = 100L)
        whenever(refundEventLogRepository.findByEventIdWithSteps(EVENT_ID)).thenReturn(null)
        whenever(refundEventLogRepository.save(any<RefundEventLog>())).thenReturn(log)
    }

    private fun message(vararg items: String): String = """
        {"eventType":"RefundCompleted","eventData":{"eventId":"$EVENT_ID","refundId":738,"orderId":100,"paymentId":200,
        "buyerId":300,"refundAmount":"45000","pointRefundAmount":"0","isFullRefund":false,"couponId":10,
        "items":[${items.joinToString(",")}]}}
    """.trimIndent()

    private fun body(index: Int): JsonNode = objectMapper.readTree(statsRequests[index].second)

    @Test
    fun 쿠폰을_쓴_주문의_환불은_실제_환불액은_amount로_정가는_본문의_product_price로_넘긴다() {
        consumer.handleRefundCompletionEvent(
            message("""{"productId":101,"quantity":2,"refundPrice":"45000","productPrice":"25000","orderItemId":9001}""")
        )

        assertThat(statsRequests).hasSize(1)
        assertThat(statsRequests[0].first)
            .isEqualTo("http://product.test/internal/api/v1/product-statistics/101/refund?quantity=2&amount=45000")
        assertThat(body(0).get("product_price").decimalValue()).isEqualByComparingTo("25000")
    }

    @Test
    fun 주문_항목_id가_없는_항목도_정가를_넘긴다() {
        consumer.handleRefundCompletionEvent(
            message("""{"productId":102,"quantity":1,"refundPrice":"9000","productPrice":"10000"}""")
        )

        assertThat(statsRequests).hasSize(1)
        assertThat(body(0).get("product_price").decimalValue()).isEqualByComparingTo("10000")
    }

    @Test
    fun 정가가_없거나_null인_옛_메시지는_본문의_product_price를_null로_넘긴다() {
        consumer.handleRefundCompletionEvent(
            message(
                """{"productId":103,"quantity":1,"refundPrice":"9000","orderItemId":9003}""",
                """{"productId":104,"quantity":1,"refundPrice":"8000","productPrice":null}"""
            )
        )

        assertThat(statsRequests).hasSize(2)
        assertThat(body(0).has("product_price")).isTrue()
        assertThat(body(0).get("product_price").isNull).isTrue()
        assertThat(body(1).has("product_price")).isTrue()
        assertThat(body(1).get("product_price").isNull).isTrue()
    }

    companion object {
        private const val EVENT_ID = "refund-completed-738"
    }
}
