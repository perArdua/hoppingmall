package com.hoppingmall.settlement.port

import com.hoppingmall.common.BusinessException
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.hamcrest.Matchers.startsWith
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpStatus
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.util.AopTestUtils
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.test.web.client.ExpectedCount
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.web.client.RestTemplate
import java.time.LocalDateTime

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("HTTP 어댑터의 뒷단 4xx 응답 처리")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class HttpAdapterClientErrorTest {

    @Autowired
    private lateinit var sellerQueryPort: SellerQueryPort

    @Autowired
    private lateinit var orderItemQueryPort: OrderItemQueryPort

    @Autowired
    private lateinit var refundQueryPort: RefundQueryPort

    @Autowired
    private lateinit var circuitBreakerRegistry: CircuitBreakerRegistry

    private lateinit var userServer: MockRestServiceServer

    private lateinit var orderItemServer: MockRestServiceServer

    private lateinit var refundServer: MockRestServiceServer

    @BeforeEach
    fun setUp() {
        listOf(SELLER_CB, ORDER_ITEM_CB, REFUND_CB).forEach { circuitBreakerRegistry.circuitBreaker(it).reset() }
        userServer = bind(sellerQueryPort)
        orderItemServer = bind(orderItemQueryPort)
        refundServer = bind(refundQueryPort)
    }

    @ParameterizedTest
    @ValueSource(ints = [400, 401, 403, 409])
    fun 판매자_조회가_404가_아닌_4xx로_거절되면_통신_오류가_아닌_호출_쪽_오류로_돌려준다(status: Int) {
        respond(userServer, SELLER_URL, status)

        assertRejected { sellerQueryPort.findByUserId(10L) }
    }

    @Test
    fun 판매자_조회의_401_응답은_회로_실패로_세지_않는다() {
        respond(userServer, SELLER_URL, 401)

        repeat(5) { runCatching { sellerQueryPort.findByUserId(10L) } }

        assertNotRecorded(SELLER_CB)
    }

    @Test
    fun 판매자_조회의_404_응답은_판매자_없음이고_회로_실패로_세지_않는다() {
        respond(userServer, SELLER_URL, 404)

        repeat(5) { assertThat(sellerQueryPort.findByUserId(10L)).isNull() }

        assertNotRecorded(SELLER_CB)
    }

    @Test
    fun 판매자_조회의_500_응답은_통신_오류이고_회로_실패로_센다() {
        respond(userServer, SELLER_URL, 500)

        assertCommunicationError { sellerQueryPort.findByUserId(10L) }
        assertThat(circuitBreakerRegistry.circuitBreaker(SELLER_CB).metrics.numberOfFailedCalls).isEqualTo(1)
    }

    @ParameterizedTest
    @ValueSource(ints = [400, 401, 403, 404, 409])
    fun 주문_항목_조회가_4xx로_거절되면_통신_오류가_아닌_호출_쪽_오류로_돌려준다(status: Int) {
        respond(orderItemServer, ORDER_ITEM_URL, status)

        assertRejected { orderItemQueryPort.findDeliveredItemsBySellerAndPeriod(1L, START, END) }
    }

    @Test
    fun 주문_항목_조회의_401_응답은_회로_실패로_세지_않는다() {
        respond(orderItemServer, ORDER_ITEM_URL, 401)

        repeat(5) { runCatching { orderItemQueryPort.findDeliveredItemsBySellerAndPeriod(1L, START, END) } }

        assertNotRecorded(ORDER_ITEM_CB)
    }

    @Test
    fun 주문_항목_조회의_500_응답은_통신_오류이고_회로_실패로_센다() {
        respond(orderItemServer, ORDER_ITEM_URL, 500)

        assertCommunicationError { orderItemQueryPort.findDeliveredItemsBySellerAndPeriod(1L, START, END) }
        assertThat(circuitBreakerRegistry.circuitBreaker(ORDER_ITEM_CB).metrics.numberOfFailedCalls).isEqualTo(1)
    }

    @ParameterizedTest
    @ValueSource(ints = [400, 401, 403, 404, 409])
    fun 환불_조회가_4xx로_거절되면_통신_오류가_아닌_호출_쪽_오류로_돌려준다(status: Int) {
        respond(refundServer, REFUND_URL, status)

        assertRejected { refundQueryPort.findCompletedBySellerAndPeriod(1L, START, END) }
    }

    @Test
    fun 환불_조회의_401_응답은_회로_실패로_세지_않는다() {
        respond(refundServer, REFUND_URL, 401)

        repeat(5) { runCatching { refundQueryPort.findCompletedBySellerAndPeriod(1L, START, END) } }

        assertNotRecorded(REFUND_CB)
    }

    @Test
    fun 환불_조회의_500_응답은_통신_오류이고_회로_실패로_센다() {
        respond(refundServer, REFUND_URL, 500)

        assertCommunicationError { refundQueryPort.findCompletedBySellerAndPeriod(1L, START, END) }
        assertThat(circuitBreakerRegistry.circuitBreaker(REFUND_CB).metrics.numberOfFailedCalls).isEqualTo(1)
    }

    private fun bind(port: Any): MockRestServiceServer {
        val target = AopTestUtils.getUltimateTargetObject<Any>(port)
        val restTemplate = ReflectionTestUtils.getField(target, "restTemplate") as RestTemplate
        return MockRestServiceServer.bindTo(restTemplate).build()
    }

    private fun respond(server: MockRestServiceServer, url: String, status: Int) {
        server.expect(ExpectedCount.manyTimes(), requestTo(startsWith(url)))
            .andRespond(withStatus(HttpStatus.valueOf(status)))
    }

    private fun assertRejected(call: () -> Unit) {
        assertThatThrownBy(call)
            .isInstanceOfSatisfying(BusinessException::class.java) { e ->
                assertThat(e.errorCode.code).isEqualTo("STL009")
                assertThat(e.errorCode.status).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR)
            }
    }

    private fun assertCommunicationError(call: () -> Unit) {
        assertThatThrownBy(call)
            .isInstanceOfSatisfying(BusinessException::class.java) { e ->
                assertThat(e.errorCode.code).isEqualTo("STL008")
                assertThat(e.errorCode.status).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
            }
    }

    private fun assertNotRecorded(name: String) {
        val circuitBreaker = circuitBreakerRegistry.circuitBreaker(name)
        assertThat(circuitBreaker.state).isEqualTo(CircuitBreaker.State.CLOSED)
        assertThat(circuitBreaker.metrics.numberOfFailedCalls).isZero()
        assertThat(circuitBreaker.metrics.numberOfNotPermittedCalls).isZero()
    }

    companion object {
        private const val SELLER_CB = "seller-query"
        private const val ORDER_ITEM_CB = "order-item-query"
        private const val REFUND_CB = "refund-query"
        private const val SELLER_URL = "http://localhost:8082/internal/api/v1/sellers/by-user/10"
        private const val ORDER_ITEM_URL = "http://localhost:8084/internal/api/v1/order-items/delivered"
        private const val REFUND_URL = "http://localhost:8084/internal/api/v1/refunds/completed"
        private val START: LocalDateTime = LocalDateTime.of(2026, 1, 1, 0, 0)
        private val END: LocalDateTime = LocalDateTime.of(2026, 2, 1, 0, 0)
    }
}
