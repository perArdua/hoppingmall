package com.hoppingmall.payment.grpc

import com.hoppingmall.mall.membership.grpc.EarningRateResponse
import com.hoppingmall.mall.membership.grpc.MembershipQueryServiceGrpc
import com.hoppingmall.order.grpc.OrderItemListResponse
import com.hoppingmall.order.grpc.OrderItemResponse
import com.hoppingmall.order.grpc.OrderQueryServiceGrpc
import com.hoppingmall.payment.port.exception.OrderItemQueryFailedException
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
import java.math.BigDecimal

@DisplayName("gRPC 조회의 일시 장애 재시도")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@SpringBootTest(classes = [GrpcQueryTransientRetryTest.TestConfig::class], webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ImportAutoConfiguration(AopAutoConfiguration::class, CircuitBreakerAutoConfiguration::class, RetryAutoConfiguration::class)
class GrpcQueryTransientRetryTest {

    @Autowired
    private lateinit var orderQueryAdapter: GrpcOrderQueryAdapter

    @Autowired
    private lateinit var membershipQueryAdapter: GrpcMembershipQueryAdapter

    @Autowired
    private lateinit var orderStub: OrderQueryServiceGrpc.OrderQueryServiceBlockingStub

    @Autowired
    private lateinit var membershipStub: MembershipQueryServiceGrpc.MembershipQueryServiceBlockingStub

    @Autowired
    private lateinit var circuitBreakerRegistry: CircuitBreakerRegistry

    @BeforeEach
    fun setUp() {
        Mockito.reset(orderStub, membershipStub)
        circuitBreakerRegistry.allCircuitBreakers.forEach { it.reset() }
    }

    @Test
    fun 주문_항목_조회가_UNAVAILABLE로_한_번_실패하면_다시_조회해_항목을_돌려준다() {
        whenever(orderStub.findOrderItemsByOrderId(any()))
            .thenThrow(StatusRuntimeException(Status.UNAVAILABLE))
            .thenReturn(itemsOf(PRODUCT_ID))

        val items = orderQueryAdapter.findOrderItemsByOrderId(ORDER_ID)

        assertThat(items).hasSize(1)
        assertThat(items.single().productId).isEqualTo(PRODUCT_ID)
        verify(orderStub, times(2)).findOrderItemsByOrderId(any())
    }

    @Test
    fun 주문_항목_조회가_DEADLINE_EXCEEDED로_한_번_실패하면_다시_조회한다() {
        whenever(orderStub.findOrderItemsByOrderId(any()))
            .thenThrow(StatusRuntimeException(Status.DEADLINE_EXCEEDED))
            .thenReturn(itemsOf(PRODUCT_ID))

        val items = orderQueryAdapter.findOrderItemsByOrderId(ORDER_ID)

        assertThat(items).hasSize(1)
        verify(orderStub, times(2)).findOrderItemsByOrderId(any())
    }

    @Test
    fun 주문_항목_조회가_세_번_모두_UNAVAILABLE이면_세_번_부른_뒤_조회_실패_예외를_던진다() {
        whenever(orderStub.findOrderItemsByOrderId(any())).thenThrow(StatusRuntimeException(Status.UNAVAILABLE))

        assertThatThrownBy { orderQueryAdapter.findOrderItemsByOrderId(ORDER_ID) }
            .isInstanceOf(OrderItemQueryFailedException::class.java)
        verify(orderStub, times(3)).findOrderItemsByOrderId(any())
    }

    @Test
    fun 주문_항목_조회가_INVALID_ARGUMENT면_다시_조회하지_않는다() {
        whenever(orderStub.findOrderItemsByOrderId(any())).thenThrow(StatusRuntimeException(Status.INVALID_ARGUMENT))

        assertThatThrownBy { orderQueryAdapter.findOrderItemsByOrderId(ORDER_ID) }
            .isInstanceOf(OrderItemQueryFailedException::class.java)
        verify(orderStub, times(1)).findOrderItemsByOrderId(any())
    }

    @Test
    fun 적립률_조회가_UNAVAILABLE로_한_번_실패하면_다시_조회해_등급_적립률을_돌려준다() {
        whenever(membershipStub.getPointEarningRate(any()))
            .thenThrow(StatusRuntimeException(Status.UNAVAILABLE))
            .thenReturn(rateOf("0.05"))

        val rate = membershipQueryAdapter.getPointEarningRate(USER_ID)

        assertThat(rate).isEqualByComparingTo(BigDecimal("0.05"))
        verify(membershipStub, times(2)).getPointEarningRate(any())
    }

    @Test
    fun 적립률_조회가_세_번_모두_UNAVAILABLE이면_세_번_부른_뒤_기본_적립률을_돌려준다() {
        whenever(membershipStub.getPointEarningRate(any())).thenThrow(StatusRuntimeException(Status.UNAVAILABLE))

        val rate = membershipQueryAdapter.getPointEarningRate(USER_ID)

        assertThat(rate).isEqualByComparingTo(BigDecimal("0.01"))
        verify(membershipStub, times(3)).getPointEarningRate(any())
    }

    @Test
    fun 적립률_조회가_NOT_FOUND면_다시_조회하지_않고_기본_적립률을_돌려준다() {
        whenever(membershipStub.getPointEarningRate(any())).thenThrow(StatusRuntimeException(Status.NOT_FOUND))

        val rate = membershipQueryAdapter.getPointEarningRate(USER_ID)

        assertThat(rate).isEqualByComparingTo(BigDecimal("0.01"))
        verify(membershipStub, times(1)).getPointEarningRate(any())
    }

    private fun itemsOf(productId: Long): OrderItemListResponse =
        OrderItemListResponse.newBuilder()
            .addItems(
                OrderItemResponse.newBuilder()
                    .setId(1L)
                    .setOrderId(ORDER_ID)
                    .setProductId(productId)
                    .setQuantity(2)
                    .setTotalPrice("20000.00")
                    .build()
            )
            .build()

    private fun rateOf(rate: String): EarningRateResponse =
        EarningRateResponse.newBuilder().setEarningRate(rate).build()

    @Configuration(proxyBeanMethods = false)
    @Import(GrpcRetryConfig::class)
    class TestConfig {
        @Bean
        fun orderQueryStub(): OrderQueryServiceGrpc.OrderQueryServiceBlockingStub = mock()

        @Bean
        fun membershipQueryStub(): MembershipQueryServiceGrpc.MembershipQueryServiceBlockingStub = mock()

        @Bean
        fun grpcOrderQueryAdapter(stub: OrderQueryServiceGrpc.OrderQueryServiceBlockingStub): GrpcOrderQueryAdapter =
            GrpcOrderQueryAdapter(stub)

        @Bean
        fun grpcMembershipQueryAdapter(stub: MembershipQueryServiceGrpc.MembershipQueryServiceBlockingStub): GrpcMembershipQueryAdapter =
            GrpcMembershipQueryAdapter(stub)
    }

    companion object {
        private const val ORDER_ID = 7L
        private const val USER_ID = 3L
        private const val PRODUCT_ID = 100L
    }
}
