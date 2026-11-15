package com.hoppingmall.product.grpc

import com.hoppingmall.order.grpc.CartAggregateResponse
import com.hoppingmall.order.grpc.CartAggregation
import com.hoppingmall.order.grpc.CartItemQueryServiceGrpc
import com.hoppingmall.order.grpc.DeliveredResponse
import com.hoppingmall.order.grpc.OrderItemListResponse
import com.hoppingmall.order.grpc.OrderItemResponse
import com.hoppingmall.order.grpc.OrderQueryServiceGrpc
import com.hoppingmall.product.review.exception.OrderQueryUnavailableException
import com.hoppingmall.product.review.port.HttpOrderQueryAdapter
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
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.boot.test.web.client.MockServerRestTemplateCustomizer
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.ExpectedCount
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withServerError
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import java.net.ConnectException

@DisplayName("gRPC 조회의 일시 장애 재시도")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@SpringBootTest(classes = [GrpcQueryTransientRetryTest.TestConfig::class], webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ImportAutoConfiguration(AopAutoConfiguration::class, CircuitBreakerAutoConfiguration::class, RetryAutoConfiguration::class)
class GrpcQueryTransientRetryTest {

    @Autowired
    private lateinit var orderItemQueryAdapter: GrpcOrderItemQueryAdapter

    @Autowired
    private lateinit var stub: OrderQueryServiceGrpc.OrderQueryServiceBlockingStub

    @Autowired
    private lateinit var orderQueryAdapter: GrpcOrderQueryAdapter

    @Autowired
    private lateinit var cartStub: CartItemQueryServiceGrpc.CartItemQueryServiceBlockingStub

    @Autowired
    private lateinit var cartItemQueryAdapter: GrpcCartItemQueryAdapter

    @Autowired
    private lateinit var httpOrderQueryAdapter: HttpOrderQueryAdapter

    @Autowired
    private lateinit var mockServerCustomizer: MockServerRestTemplateCustomizer

    @Autowired
    private lateinit var circuitBreakerRegistry: CircuitBreakerRegistry

    @BeforeEach
    fun setUp() {
        Mockito.reset(stub, cartStub)
        mockServerCustomizer.servers.values.forEach { it.reset() }
        circuitBreakerRegistry.allCircuitBreakers.forEach { it.reset() }
    }

    @Test
    fun 주문_항목_조회가_UNAVAILABLE로_한_번_실패하면_다시_조회해_항목을_돌려준다() {
        whenever(stub.findOrderItemsByOrderId(any()))
            .thenThrow(StatusRuntimeException(Status.UNAVAILABLE))
            .thenReturn(itemsOf(ORDER_ID))

        val items = orderItemQueryAdapter.findByOrderId(ORDER_ID)

        assertThat(items).hasSize(1)
        assertThat(items.single().productId).isEqualTo(PRODUCT_ID)
        verify(stub, times(2)).findOrderItemsByOrderId(any())
    }

    @Test
    fun 주문_항목_조회가_DEADLINE_EXCEEDED로_한_번_실패하면_다시_조회한다() {
        whenever(stub.findOrderItemsByOrderId(any()))
            .thenThrow(StatusRuntimeException(Status.DEADLINE_EXCEEDED))
            .thenReturn(itemsOf(ORDER_ID))

        val items = orderItemQueryAdapter.findByOrderId(ORDER_ID)

        assertThat(items).hasSize(1)
        verify(stub, times(2)).findOrderItemsByOrderId(any())
    }

    @Test
    fun 주문_항목_조회가_세_번_모두_UNAVAILABLE이면_세_번_부른_뒤_실패한다() {
        whenever(stub.findOrderItemsByOrderId(any())).thenThrow(StatusRuntimeException(Status.UNAVAILABLE))

        assertThatThrownBy { orderItemQueryAdapter.findByOrderId(ORDER_ID) }
            .isInstanceOf(StatusRuntimeException::class.java)
        verify(stub, times(3)).findOrderItemsByOrderId(any())
    }

    @Test
    fun 주문_항목_조회가_NOT_FOUND면_다시_조회하지_않는다() {
        whenever(stub.findOrderItemsByOrderId(any())).thenThrow(StatusRuntimeException(Status.NOT_FOUND))

        assertThatThrownBy { orderItemQueryAdapter.findByOrderId(ORDER_ID) }
            .isInstanceOf(StatusRuntimeException::class.java)
        verify(stub, times(1)).findOrderItemsByOrderId(any())
    }

    @Test
    fun 주문_항목_조회가_INVALID_ARGUMENT면_다시_조회하지_않는다() {
        whenever(stub.findOrderItemsByOrderId(any())).thenThrow(StatusRuntimeException(Status.INVALID_ARGUMENT))

        assertThatThrownBy { orderItemQueryAdapter.findByOrderId(ORDER_ID) }
            .isInstanceOf(StatusRuntimeException::class.java)
        verify(stub, times(1)).findOrderItemsByOrderId(any())
    }

    @Test
    fun 배송_완료_확인이_UNAVAILABLE로_한_번_실패하면_다시_확인해_답을_돌려준다() {
        whenever(stub.isDelivered(any()))
            .thenThrow(StatusRuntimeException(Status.UNAVAILABLE))
            .thenReturn(DeliveredResponse.newBuilder().setDelivered(true).build())

        assertThat(orderQueryAdapter.isDelivered(ORDER_ID, BUYER_ID)).isTrue()
        verify(stub, times(2)).isDelivered(any())
    }

    @Test
    fun 배송_완료_확인이_끝내_실패하면_배송_전이_아니라_주문_조회_불가로_알린다() {
        whenever(stub.isDelivered(any())).thenThrow(StatusRuntimeException(Status.UNAVAILABLE))

        assertThatThrownBy { orderQueryAdapter.isDelivered(ORDER_ID, BUYER_ID) }
            .isInstanceOf(OrderQueryUnavailableException::class.java)
        verify(stub, times(3)).isDelivered(any())
    }

    @Test
    fun 주문_상품_단건_조회가_DEADLINE_EXCEEDED로_한_번_실패하면_다시_조회한다() {
        whenever(stub.findOrderItemById(any()))
            .thenThrow(StatusRuntimeException(Status.DEADLINE_EXCEEDED))
            .thenReturn(itemOf(ORDER_ID))

        assertThat(orderQueryAdapter.findOrderItemById(ORDER_ITEM_ID)!!.productId).isEqualTo(PRODUCT_ID)
        verify(stub, times(2)).findOrderItemById(any())
    }

    @Test
    fun 주문_상품_단건_조회가_NOT_FOUND면_다시_조회하지_않고_없음으로_답한다() {
        whenever(stub.findOrderItemById(any())).thenThrow(StatusRuntimeException(Status.NOT_FOUND))

        assertThat(orderQueryAdapter.findOrderItemById(ORDER_ITEM_ID)).isNull()
        verify(stub, times(1)).findOrderItemById(any())
    }

    @Test
    fun 주문_상품_단건_조회가_끝내_실패하면_없는_상품이_아니라_주문_조회_불가로_알린다() {
        whenever(stub.findOrderItemById(any())).thenThrow(StatusRuntimeException(Status.UNAVAILABLE))

        assertThatThrownBy { orderQueryAdapter.findOrderItemById(ORDER_ITEM_ID) }
            .isInstanceOf(OrderQueryUnavailableException::class.java)
        verify(stub, times(3)).findOrderItemById(any())
    }

    @Test
    fun 주문별_상품_목록_조회가_UNAVAILABLE로_한_번_실패하면_다시_조회한다() {
        whenever(stub.findOrderItemsByOrderId(any()))
            .thenThrow(StatusRuntimeException(Status.UNAVAILABLE))
            .thenReturn(itemsOf(ORDER_ID))

        assertThat(orderQueryAdapter.findOrderItemsByOrderId(ORDER_ID)).hasSize(1)
        verify(stub, times(2)).findOrderItemsByOrderId(any())
    }

    @Test
    fun 주문별_상품_목록_조회가_끝내_실패하면_빈_목록이_아니라_주문_조회_불가로_알린다() {
        whenever(stub.findOrderItemsByOrderId(any())).thenThrow(StatusRuntimeException(Status.UNAVAILABLE))

        assertThatThrownBy { orderQueryAdapter.findOrderItemsByOrderId(ORDER_ID) }
            .isInstanceOf(OrderQueryUnavailableException::class.java)
        verify(stub, times(3)).findOrderItemsByOrderId(any())
    }

    @Test
    fun 주문_조회_회로가_열려_있으면_부르지_않고_주문_조회_불가로_알린다() {
        circuitBreakerRegistry.circuitBreaker("order-query").transitionToOpenState()

        assertThatThrownBy { orderQueryAdapter.isDelivered(ORDER_ID, BUYER_ID) }
            .isInstanceOf(OrderQueryUnavailableException::class.java)
        verify(stub, times(0)).isDelivered(any())
    }

    @Test
    fun 장바구니_집계가_UNAVAILABLE로_한_번_실패하면_다시_조회한다() {
        whenever(cartStub.aggregateCartByProduct(any()))
            .thenThrow(StatusRuntimeException(Status.UNAVAILABLE))
            .thenReturn(
                CartAggregateResponse.newBuilder()
                    .addAggregations(CartAggregation.newBuilder().setProductId(PRODUCT_ID).setBuyerCount(4).build())
                    .build()
            )

        val aggregations = cartItemQueryAdapter.aggregateCartByProduct()

        assertThat(aggregations.single().buyerCount).isEqualTo(4L)
        verify(cartStub, times(2)).aggregateCartByProduct(any())
    }

    @Test
    fun 장바구니_집계가_끝내_실패하면_세_번_부른_뒤_빈_목록을_준다() {
        whenever(cartStub.aggregateCartByProduct(any())).thenThrow(StatusRuntimeException(Status.UNAVAILABLE))

        assertThat(cartItemQueryAdapter.aggregateCartByProduct()).isEmpty()
        verify(cartStub, times(3)).aggregateCartByProduct(any())
    }

    @Test
    fun HTTP_배송_완료_확인이_연결_실패로_한_번_실패하면_다시_확인한다() {
        val server = httpServer()
        server.expect(ExpectedCount.once(), requestTo(DELIVERED_URL)).andRespond { throw ConnectException("Connection refused") }
        server.expect(ExpectedCount.once(), requestTo(DELIVERED_URL)).andRespond(withSuccess("true", MediaType.APPLICATION_JSON))

        assertThat(httpOrderQueryAdapter.isDelivered(ORDER_ID, BUYER_ID)).isTrue()
        server.verify()
    }

    @Test
    fun HTTP_배송_완료_확인이_5xx면_배송_전이_아니라_주문_조회_불가로_알린다() {
        val server = httpServer()
        server.expect(ExpectedCount.once(), requestTo(DELIVERED_URL)).andRespond(withServerError())

        assertThatThrownBy { httpOrderQueryAdapter.isDelivered(ORDER_ID, BUYER_ID) }
            .isInstanceOf(OrderQueryUnavailableException::class.java)
        server.verify()
    }

    @Test
    fun HTTP_주문_상품_조회가_404면_없음으로_답한다() {
        val server = httpServer()
        server.expect(ExpectedCount.once(), requestTo(ORDER_ITEM_URL)).andRespond(withStatus(HttpStatus.NOT_FOUND))

        assertThat(httpOrderQueryAdapter.findOrderItemById(ORDER_ITEM_ID)).isNull()
        server.verify()
    }

    @Test
    fun HTTP_주문_상품_조회가_5xx면_없는_상품이_아니라_주문_조회_불가로_알린다() {
        val server = httpServer()
        server.expect(ExpectedCount.once(), requestTo(ORDER_ITEM_URL)).andRespond(withServerError())

        assertThatThrownBy { httpOrderQueryAdapter.findOrderItemById(ORDER_ITEM_ID) }
            .isInstanceOf(OrderQueryUnavailableException::class.java)
        server.verify()
    }

    private fun httpServer() = mockServerCustomizer.servers.values.single()

    private fun itemOf(orderId: Long): OrderItemResponse =
        OrderItemResponse.newBuilder()
            .setId(ORDER_ITEM_ID)
            .setOrderId(orderId)
            .setProductId(PRODUCT_ID)
            .setQuantity(2)
            .setTotalPrice("20000.00")
            .build()

    private fun itemsOf(orderId: Long): OrderItemListResponse =
        OrderItemListResponse.newBuilder()
            .addItems(
                OrderItemResponse.newBuilder()
                    .setId(1L)
                    .setOrderId(orderId)
                    .setProductId(PRODUCT_ID)
                    .setQuantity(2)
                    .setTotalPrice("20000.00")
                    .build()
            )
            .build()

    @Configuration(proxyBeanMethods = false)
    @Import(GrpcRetryConfig::class)
    class TestConfig {
        @Bean
        fun orderQueryStub(): OrderQueryServiceGrpc.OrderQueryServiceBlockingStub = mock()

        @Bean
        fun grpcOrderItemQueryAdapter(stub: OrderQueryServiceGrpc.OrderQueryServiceBlockingStub): GrpcOrderItemQueryAdapter =
            GrpcOrderItemQueryAdapter(stub)

        @Bean
        fun grpcOrderQueryAdapter(stub: OrderQueryServiceGrpc.OrderQueryServiceBlockingStub): GrpcOrderQueryAdapter =
            GrpcOrderQueryAdapter(stub)

        @Bean
        fun cartItemQueryStub(): CartItemQueryServiceGrpc.CartItemQueryServiceBlockingStub = mock()

        @Bean
        fun grpcCartItemQueryAdapter(stub: CartItemQueryServiceGrpc.CartItemQueryServiceBlockingStub): GrpcCartItemQueryAdapter =
            GrpcCartItemQueryAdapter(stub)

        @Bean
        fun mockServerRestTemplateCustomizer(): MockServerRestTemplateCustomizer = MockServerRestTemplateCustomizer()

        @Bean
        fun httpOrderQueryAdapter(customizer: MockServerRestTemplateCustomizer): HttpOrderQueryAdapter =
            HttpOrderQueryAdapter(ORDER_URL, RestTemplateBuilder(customizer))
    }

    companion object {
        private const val ORDER_ID = 7L
        private const val BUYER_ID = 3L
        private const val ORDER_ITEM_ID = 11L
        private const val ORDER_URL = "http://order-service.test"
        private const val DELIVERED_URL = "$ORDER_URL/internal/api/v1/orders/$ORDER_ID/delivered?buyerId=$BUYER_ID"
        private const val ORDER_ITEM_URL = "$ORDER_URL/internal/api/v1/order-items/$ORDER_ITEM_ID"
        private const val PRODUCT_ID = 100L
    }
}
