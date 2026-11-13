package com.hoppingmall.product.statistics.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.hoppingmall.common.event.KafkaEventMapper
import com.hoppingmall.order.grpc.OrderIdRequest
import com.hoppingmall.order.grpc.OrderItemListResponse
import com.hoppingmall.order.grpc.OrderItemResponse
import com.hoppingmall.order.grpc.OrderQueryServiceGrpc
import com.hoppingmall.product.grpc.GrpcOrderItemQueryAdapter
import com.hoppingmall.product.product.domain.StatisticsEventLog
import com.hoppingmall.product.product.domain.repository.StatisticsEventLogRepository
import com.hoppingmall.product.product.service.ProductStatisticsCommandService
import com.hoppingmall.product.statistics.port.OrderItemQueryPort
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.github.resilience4j.springboot3.circuitbreaker.autoconfigure.CircuitBreakerAutoConfiguration
import io.github.resilience4j.springboot3.retry.autoconfigure.RetryAutoConfiguration
import io.grpc.ManagedChannel
import io.grpc.Server
import io.grpc.Status
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import io.grpc.stub.StreamObserver
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.math.BigDecimal

@DisplayName("주문 항목 조회 실패 때의 통계 이벤트 처리")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@SpringBootTest(
    classes = [StatisticsOrderItemQueryFailureTest.OrderItemQueryConfig::class],
    webEnvironment = SpringBootTest.WebEnvironment.NONE
)
class StatisticsOrderItemQueryFailureTest {

    @Autowired
    private lateinit var orderItemQueryPort: OrderItemQueryPort

    @Autowired
    private lateinit var orderService: FakeOrderQueryService

    @Autowired
    private lateinit var circuitBreakerRegistry: CircuitBreakerRegistry

    private val statisticsEventLogRepository: StatisticsEventLogRepository = mock()

    private val productStatisticsCommandService: ProductStatisticsCommandService = mock()

    private lateinit var consumer: StatisticsEventConsumer

    @BeforeEach
    fun setUp() {
        circuitBreakerRegistry.allCircuitBreakers.forEach { it.reset() }
        orderService.failure = null
        val objectMapper = ObjectMapper().registerModule(JavaTimeModule())
        consumer = StatisticsEventConsumer(
            statisticsEventLogRepository,
            orderItemQueryPort,
            productStatisticsCommandService,
            objectMapper,
            KafkaEventMapper(objectMapper)
        )
        whenever(statisticsEventLogRepository.existsByEventId(any())).thenReturn(false)
    }

    @Test
    fun 주문_항목을_읽으면_결제_완료를_판매_통계에_반영하고_처리_완료로_남긴다() {
        whenever(statisticsEventLogRepository.save(any<StatisticsEventLog>())).thenAnswer { it.arguments[0] }

        consumer.handlePaymentCompleted(COMPLETED_MESSAGE)

        verify(productStatisticsCommandService).incrementSalesStats(PRODUCT_ID, 3L, BigDecimal("3000"))
        verify(statisticsEventLogRepository).save(any<StatisticsEventLog>())
    }

    @Test
    fun 주문_항목_조회가_실패하면_결제_완료_이벤트를_처리_완료로_남기지_않고_예외로_끝낸다() {
        orderService.failure = Status.UNAVAILABLE.withDescription("order 연결 불가")

        val result = runCatching { consumer.handlePaymentCompleted(COMPLETED_MESSAGE) }

        assertThat(result.isFailure).isTrue()
        verify(statisticsEventLogRepository, never()).save(any<StatisticsEventLog>())
        verify(productStatisticsCommandService, never()).incrementSalesStats(any(), any(), any())
    }

    @Test
    fun 주문_항목_조회가_실패하면_결제_취소_이벤트를_처리_완료로_남기지_않고_예외로_끝낸다() {
        whenever(statisticsEventLogRepository.existsByEventId(TRANSACTION_ID)).thenReturn(true)
        orderService.failure = Status.UNAVAILABLE.withDescription("order 연결 불가")

        val result = runCatching { consumer.handleCompensationEvent(CANCELLED_MESSAGE) }

        assertThat(result.isFailure).isTrue()
        verify(statisticsEventLogRepository, never()).save(any<StatisticsEventLog>())
        verify(productStatisticsCommandService, never()).decrementSalesStats(any(), any(), any())
    }

    @Test
    fun 주문_항목_조회가_실패하면_역보상_이벤트를_처리_완료로_남기지_않고_예외로_끝낸다() {
        whenever(statisticsEventLogRepository.existsByOrderIdAndEventType(ORDER_ID, "PaymentCompleted")).thenReturn(true)
        orderService.failure = Status.UNAVAILABLE.withDescription("order 연결 불가")

        val result = runCatching { consumer.handlePaymentReversal(REVERSAL_MESSAGE) }

        assertThat(result.isFailure).isTrue()
        verify(statisticsEventLogRepository, never()).save(any<StatisticsEventLog>())
        verify(productStatisticsCommandService, never()).decrementSalesStats(any(), any(), any())
    }

    @Configuration
    @ImportAutoConfiguration(AopAutoConfiguration::class, CircuitBreakerAutoConfiguration::class, RetryAutoConfiguration::class)
    class OrderItemQueryConfig {

        private val serverName = InProcessServerBuilder.generateName()

        @Bean
        fun fakeOrderQueryService() = FakeOrderQueryService()

        @Bean(destroyMethod = "shutdownNow")
        fun orderQueryServer(service: FakeOrderQueryService): Server =
            InProcessServerBuilder.forName(serverName).addService(service).build().start()

        @Bean(destroyMethod = "shutdownNow")
        fun orderQueryChannel(orderQueryServer: Server): ManagedChannel =
            InProcessChannelBuilder.forName(serverName).build()

        @Bean
        fun grpcOrderItemQueryAdapter(orderQueryChannel: ManagedChannel) =
            GrpcOrderItemQueryAdapter(OrderQueryServiceGrpc.newBlockingStub(orderQueryChannel))
    }

    class FakeOrderQueryService : OrderQueryServiceGrpc.OrderQueryServiceImplBase() {

        @Volatile
        var failure: Status? = null

        override fun findOrderItemsByOrderId(request: OrderIdRequest, observer: StreamObserver<OrderItemListResponse>) {
            failure?.let {
                observer.onError(it.asRuntimeException())
                return
            }
            observer.onNext(
                OrderItemListResponse.newBuilder()
                    .addItems(
                        OrderItemResponse.newBuilder()
                            .setId(1L)
                            .setOrderId(request.orderId)
                            .setProductId(PRODUCT_ID)
                            .setQuantity(3)
                            .setTotalPrice("3000")
                    )
                    .build()
            )
            observer.onCompleted()
        }
    }

    companion object {
        private const val ORDER_ID = 9_411L
        private const val PRODUCT_ID = 9_412L
        private const val TRANSACTION_ID = "txn-9413"
        private const val COMPLETED_MESSAGE =
            """{"paymentId":9413,"orderId":$ORDER_ID,"userId":1,"amount":3000,"pointAmount":0,"transactionId":"$TRANSACTION_ID","completedAt":"2026-11-13T07:41:31"}"""
        private const val CANCELLED_MESSAGE =
            """{"eventType":"PaymentCancelled","eventId":"payment-cancelled-9413","paymentId":9413,"orderId":$ORDER_ID,"userId":1,"amount":3000,"transactionId":"$TRANSACTION_ID"}"""
        private const val REVERSAL_MESSAGE =
            """{"eventType":"PaymentReversalRequested","eventId":"reversal-9413","orderId":$ORDER_ID}"""
    }
}
