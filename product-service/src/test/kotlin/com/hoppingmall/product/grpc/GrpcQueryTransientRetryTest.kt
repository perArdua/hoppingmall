package com.hoppingmall.product.grpc

import com.hoppingmall.order.grpc.OrderItemListResponse
import com.hoppingmall.order.grpc.OrderItemResponse
import com.hoppingmall.order.grpc.OrderQueryServiceGrpc
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
    private lateinit var circuitBreakerRegistry: CircuitBreakerRegistry

    @BeforeEach
    fun setUp() {
        Mockito.reset(stub)
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
    }

    companion object {
        private const val ORDER_ID = 7L
        private const val PRODUCT_ID = 100L
    }
}
