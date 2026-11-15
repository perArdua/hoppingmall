package com.hoppingmall.order.support

import com.hoppingmall.order.cartItem.service.CartItemCommandServiceImpl
import com.hoppingmall.order.cartItem.service.CartItemQueryServiceImpl
import com.hoppingmall.order.config.OrderMetrics
import com.hoppingmall.order.internal.InternalOrderController
import com.hoppingmall.order.order.service.OrderCommandServiceImpl
import com.hoppingmall.order.order.service.ReservationFence
import com.hoppingmall.order.port.InventoryCommandPort
import com.hoppingmall.order.port.PaymentCommandPort
import com.hoppingmall.order.port.PaymentQueryPort
import com.hoppingmall.order.port.ProductQueryPort
import com.hoppingmall.order.refund.service.RefundCommandServiceImpl
import com.hoppingmall.order.refund.service.RefundEventPublisher
import com.hoppingmall.outbox.service.TransactionalEventPublisher
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

@Import(
    CartItemCommandServiceImpl::class,
    CartItemQueryServiceImpl::class,
    OrderCommandServiceImpl::class,
    ReservationFence::class,
    OrderMetrics::class,
    InternalOrderController::class,
    RefundCommandServiceImpl::class,
    OrderMySqlServiceTestSupport.ServiceBeans::class
)
abstract class OrderMySqlServiceTestSupport : MySqlJpaTestSupport() {

    @MockitoBean
    protected lateinit var productQueryPort: ProductQueryPort

    @MockitoBean
    protected lateinit var inventoryCommandPort: InventoryCommandPort

    @MockitoBean
    protected lateinit var paymentCommandPort: PaymentCommandPort

    @MockitoBean
    protected lateinit var paymentQueryPort: PaymentQueryPort

    @MockitoBean
    protected lateinit var transactionalEventPublisher: TransactionalEventPublisher

    @MockitoBean
    protected lateinit var refundEventPublisher: RefundEventPublisher

    @TestConfiguration(proxyBeanMethods = false)
    class ServiceBeans {
        @Bean
        fun transactionTemplate(transactionManager: PlatformTransactionManager): TransactionTemplate =
            TransactionTemplate(transactionManager)

        @Bean
        fun meterRegistry(): MeterRegistry = SimpleMeterRegistry()
    }
}
