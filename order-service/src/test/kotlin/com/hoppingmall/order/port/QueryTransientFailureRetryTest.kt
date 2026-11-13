package com.hoppingmall.order.port

import com.hoppingmall.order.grpc.GrpcPaymentQueryAdapter
import com.hoppingmall.payment.grpc.PaymentQueryServiceGrpc
import com.hoppingmall.payment.grpc.PaymentResponse
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.github.resilience4j.springboot3.circuitbreaker.autoconfigure.CircuitBreakerAutoConfiguration
import io.github.resilience4j.springboot3.retry.autoconfigure.RetryAutoConfiguration
import io.grpc.Status
import io.grpc.StatusRuntimeException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.MockServerRestTemplateCustomizer
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.ExpectedCount
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withException
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import java.io.IOException

@DisplayName("상품·결제 조회의 일시 장애 재시도")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@SpringBootTest(classes = [QueryTransientFailureRetryTest.TestConfig::class], webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ImportAutoConfiguration(AopAutoConfiguration::class, CircuitBreakerAutoConfiguration::class, RetryAutoConfiguration::class)
class QueryTransientFailureRetryTest {

    @Autowired
    private lateinit var productQueryAdapter: HttpProductQueryAdapter

    @Autowired
    private lateinit var paymentQueryAdapter: HttpPaymentQueryAdapter

    @Autowired
    private lateinit var grpcPaymentQueryAdapter: GrpcPaymentQueryAdapter

    @Autowired
    private lateinit var paymentStub: PaymentQueryServiceGrpc.PaymentQueryServiceBlockingStub

    @Autowired
    @Qualifier("productServer")
    private lateinit var productServer: MockServerRestTemplateCustomizer

    @Autowired
    @Qualifier("paymentServer")
    private lateinit var paymentServer: MockServerRestTemplateCustomizer

    @Autowired
    private lateinit var circuitBreakerRegistry: CircuitBreakerRegistry

    @BeforeEach
    fun setUp() {
        Mockito.reset(paymentStub)
        productServer.server.reset()
        paymentServer.server.reset()
        circuitBreakerRegistry.allCircuitBreakers.forEach { it.reset() }
    }

    @Test
    fun HTTP_상품_단건_조회가_I_O_오류로_한_번_실패하면_다시_조회해_상품을_돌려준다() {
        val url = "$PRODUCT_URL/internal/api/v1/products/$PRODUCT_ID"
        productServer.server.expect(requestTo(url)).andRespond(withException(IOException("connection reset")))
        productServer.server.expect(requestTo(url)).andRespond(withSuccess(PRODUCT_JSON, MediaType.APPLICATION_JSON))

        val product = productQueryAdapter.findProductById(PRODUCT_ID)

        assertThat(product!!.id).isEqualTo(PRODUCT_ID)
        productServer.server.verify()
    }

    @Test
    fun HTTP_상품_목록_조회가_I_O_오류로_한_번_실패하면_다시_조회해_목록을_돌려준다() {
        val url = "$PRODUCT_URL/internal/api/v1/products?ids=$PRODUCT_ID"
        productServer.server.expect(requestTo(url)).andRespond(withException(IOException("connection reset")))
        productServer.server.expect(requestTo(url)).andRespond(withSuccess("[$PRODUCT_JSON]", MediaType.APPLICATION_JSON))

        val products = productQueryAdapter.findProductsByIds(listOf(PRODUCT_ID))

        assertThat(products.map { it.id }).containsExactly(PRODUCT_ID)
        productServer.server.verify()
    }

    @Test
    fun HTTP_상품_조회가_404면_다시_조회하지_않고_없음으로_돌려준다() {
        productServer.server.expect(ExpectedCount.once(), requestTo("$PRODUCT_URL/internal/api/v1/products/$PRODUCT_ID"))
            .andRespond(withStatus(HttpStatus.NOT_FOUND))

        val product = productQueryAdapter.findProductById(PRODUCT_ID)

        assertThat(product).isNull()
        productServer.server.verify()
    }

    @Test
    fun HTTP_주문별_결제_조회가_I_O_오류로_한_번_실패하면_다시_조회해_결제를_돌려준다() {
        val url = "$PAYMENT_URL/internal/api/v1/payments/by-order/$ORDER_ID"
        paymentServer.server.expect(requestTo(url)).andRespond(withException(IOException("connection reset")))
        paymentServer.server.expect(requestTo(url)).andRespond(withSuccess(PAYMENT_JSON, MediaType.APPLICATION_JSON))

        val payment = paymentQueryAdapter.findByOrderId(ORDER_ID)

        assertThat(payment!!.id).isEqualTo(PAYMENT_ID)
        paymentServer.server.verify()
    }

    @Test
    fun HTTP_결제_단건_조회가_I_O_오류로_한_번_실패하면_다시_조회해_결제를_돌려준다() {
        val url = "$PAYMENT_URL/internal/api/v1/payments/$PAYMENT_ID"
        paymentServer.server.expect(requestTo(url)).andRespond(withException(IOException("connection reset")))
        paymentServer.server.expect(requestTo(url)).andRespond(withSuccess(PAYMENT_JSON, MediaType.APPLICATION_JSON))

        val payment = paymentQueryAdapter.findById(PAYMENT_ID)

        assertThat(payment!!.id).isEqualTo(PAYMENT_ID)
        paymentServer.server.verify()
    }

    @Test
    fun HTTP_조회_불가를_구분하는_결제_조회가_I_O_오류로_한_번_실패하면_다시_조회해_결제를_돌려준다() {
        val url = "$PAYMENT_URL/internal/api/v1/payments/by-order/$ORDER_ID"
        paymentServer.server.expect(requestTo(url)).andRespond(withException(IOException("connection reset")))
        paymentServer.server.expect(requestTo(url)).andRespond(withSuccess(PAYMENT_JSON, MediaType.APPLICATION_JSON))

        val payment = paymentQueryAdapter.findByOrderIdOrThrowIfUnavailable(ORDER_ID)

        assertThat(payment!!.id).isEqualTo(PAYMENT_ID)
        paymentServer.server.verify()
    }

    @Test
    fun HTTP_주문별_결제_조회가_재시도를_다_써도_실패하면_세_번_부른_뒤_없음으로_돌려준다() {
        paymentServer.server.expect(ExpectedCount.times(3), requestTo("$PAYMENT_URL/internal/api/v1/payments/by-order/$ORDER_ID"))
            .andRespond(withException(IOException("connection reset")))

        val payment = paymentQueryAdapter.findByOrderId(ORDER_ID)

        assertThat(payment).isNull()
        paymentServer.server.verify()
    }

    @Test
    fun HTTP_조회_불가를_구분하는_결제_조회가_재시도를_다_써도_실패하면_조회_불가_예외를_던진다() {
        paymentServer.server.expect(ExpectedCount.times(3), requestTo("$PAYMENT_URL/internal/api/v1/payments/by-order/$ORDER_ID"))
            .andRespond(withException(IOException("connection reset")))

        assertThatThrownBy { paymentQueryAdapter.findByOrderIdOrThrowIfUnavailable(ORDER_ID) }
            .isInstanceOf(PaymentQueryUnavailableException::class.java)
        paymentServer.server.verify()
    }

    @Test
    fun gRPC_주문별_결제_조회가_UNAVAILABLE로_한_번_실패하면_다시_조회해_결제를_돌려준다() {
        whenever(paymentStub.findPaymentByOrderId(any()))
            .thenThrow(StatusRuntimeException(Status.UNAVAILABLE))
            .thenReturn(PAYMENT_RESPONSE)

        val payment = grpcPaymentQueryAdapter.findByOrderId(ORDER_ID)

        assertThat(payment!!.id).isEqualTo(PAYMENT_ID)
        verify(paymentStub, times(2)).findPaymentByOrderId(any())
    }

    @Test
    fun gRPC_결제_단건_조회가_DEADLINE_EXCEEDED로_한_번_실패하면_다시_조회해_결제를_돌려준다() {
        whenever(paymentStub.findPaymentById(any()))
            .thenThrow(StatusRuntimeException(Status.DEADLINE_EXCEEDED))
            .thenReturn(PAYMENT_RESPONSE)

        val payment = grpcPaymentQueryAdapter.findById(PAYMENT_ID)

        assertThat(payment!!.id).isEqualTo(PAYMENT_ID)
        verify(paymentStub, times(2)).findPaymentById(any())
    }

    @Test
    fun gRPC_조회_불가를_구분하는_결제_조회가_UNAVAILABLE로_한_번_실패하면_다시_조회해_결제를_돌려준다() {
        whenever(paymentStub.findPaymentByOrderId(any()))
            .thenThrow(StatusRuntimeException(Status.UNAVAILABLE))
            .thenReturn(PAYMENT_RESPONSE)

        val payment = grpcPaymentQueryAdapter.findByOrderIdOrThrowIfUnavailable(ORDER_ID)

        assertThat(payment!!.id).isEqualTo(PAYMENT_ID)
        verify(paymentStub, times(2)).findPaymentByOrderId(any())
    }

    @Test
    fun gRPC_결제_조회가_NOT_FOUND면_다시_조회하지_않고_없음으로_돌려준다() {
        whenever(paymentStub.findPaymentByOrderId(any())).thenThrow(StatusRuntimeException(Status.NOT_FOUND))

        val payment = grpcPaymentQueryAdapter.findByOrderIdOrThrowIfUnavailable(ORDER_ID)

        assertThat(payment).isNull()
        verify(paymentStub, times(1)).findPaymentByOrderId(any())
    }

    @Configuration(proxyBeanMethods = false)
    class TestConfig {
        @Bean
        fun productServer(): MockServerRestTemplateCustomizer = MockServerRestTemplateCustomizer()

        @Bean
        fun paymentServer(): MockServerRestTemplateCustomizer = MockServerRestTemplateCustomizer()

        @Bean
        fun httpProductQueryAdapter(@Qualifier("productServer") customizer: MockServerRestTemplateCustomizer): HttpProductQueryAdapter =
            HttpProductQueryAdapter(PRODUCT_URL, RestTemplateBuilder(customizer))

        @Bean
        fun httpPaymentQueryAdapter(@Qualifier("paymentServer") customizer: MockServerRestTemplateCustomizer): HttpPaymentQueryAdapter =
            HttpPaymentQueryAdapter(PAYMENT_URL, RestTemplateBuilder(customizer))

        @Bean
        fun paymentQueryStub(): PaymentQueryServiceGrpc.PaymentQueryServiceBlockingStub = mock()

        @Bean
        fun grpcPaymentQueryAdapter(stub: PaymentQueryServiceGrpc.PaymentQueryServiceBlockingStub): GrpcPaymentQueryAdapter =
            GrpcPaymentQueryAdapter(stub)
    }

    companion object {
        private const val PRODUCT_ID = 1L
        private const val ORDER_ID = 10L
        private const val PAYMENT_ID = 5L
        private const val PRODUCT_URL = "http://product-service"
        private const val PAYMENT_URL = "http://payment-service"
        private const val PRODUCT_JSON = """{"id":1,"name":"상품A","price":5000.00,"sellerId":2}"""
        private const val PAYMENT_JSON =
            """{"id":5,"orderId":10,"amount":50000,"pointAmount":0,"couponId":null,"status":"SUCCESS"}"""
        private val PAYMENT_RESPONSE: PaymentResponse = PaymentResponse.newBuilder()
            .setId(PAYMENT_ID)
            .setOrderId(ORDER_ID)
            .setAmount("50000")
            .setPointAmount("0")
            .setStatus("SUCCESS")
            .build()
    }
}
