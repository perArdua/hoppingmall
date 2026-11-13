package com.hoppingmall.product.statistics.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.hoppingmall.common.event.KafkaEventMapper
import com.hoppingmall.product.category.dto.request.CategoryCreateRequest
import com.hoppingmall.product.category.service.CategoryCommandService
import com.hoppingmall.product.common.enums.ProductStatus
import com.hoppingmall.product.product.domain.ProductStatistics
import com.hoppingmall.product.product.domain.StatisticsEventLog
import com.hoppingmall.product.product.domain.repository.ProductStatisticsRepository
import com.hoppingmall.product.product.domain.repository.StatisticsEventLogRepository
import com.hoppingmall.product.product.dto.request.ProductCreateRequest
import com.hoppingmall.product.product.service.ProductCommandService
import com.hoppingmall.product.product.service.ProductStatisticsCommandService
import com.hoppingmall.product.statistics.port.OrderItemInfo
import com.hoppingmall.product.statistics.port.OrderItemQueryPort
import com.hoppingmall.product.support.MySqlIntegrationTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal

@DisplayName("결제 완료·취소 이벤트 순서에 따른 상품 통계 MySQL 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class StatisticsCancelOrderingMySqlIntegrationTest : MySqlIntegrationTestSupport() {

    @Autowired
    private lateinit var statisticsEventLogRepository: StatisticsEventLogRepository

    @Autowired
    private lateinit var productStatisticsCommandService: ProductStatisticsCommandService

    @Autowired
    private lateinit var productStatisticsRepository: ProductStatisticsRepository

    @Autowired
    private lateinit var productCommandService: ProductCommandService

    @Autowired
    private lateinit var categoryCommandService: CategoryCommandService

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var kafkaEventMapper: KafkaEventMapper

    @Autowired
    private lateinit var transactionTemplate: TransactionTemplate

    private val orderItemQueryPort: OrderItemQueryPort = mock()

    private lateinit var consumer: StatisticsEventConsumer

    private var productId: Long = 0

    @BeforeEach
    fun setUp() {
        STATISTICS_TABLES.forEach { jdbcTemplate.update("DELETE FROM $it") }
        consumer = StatisticsEventConsumer(
            statisticsEventLogRepository,
            orderItemQueryPort,
            productStatisticsCommandService,
            objectMapper,
            kafkaEventMapper,
            transactionTemplate
        )
        productId = createProduct()
        whenever(orderItemQueryPort.findByOrderId(ORDER_ID)).thenReturn(
            listOf(OrderItemInfo(id = 1L, orderId = ORDER_ID, productId = productId, quantity = 3, totalPrice = BigDecimal("3000")))
        )
        whenever(orderItemQueryPort.findByOrderId(EARLIER_ORDER_ID)).thenReturn(
            listOf(OrderItemInfo(id = 2L, orderId = EARLIER_ORDER_ID, productId = productId, quantity = 2, totalPrice = BigDecimal("2000")))
        )
    }

    @AfterEach
    fun tearDown() {
        STATISTICS_TABLES.forEach { jdbcTemplate.update("DELETE FROM $it") }
    }

    @Test
    fun 결제_완료_뒤에_취소가_오면_판매_통계가_0으로_돌아온다() {
        consumer.handlePaymentCompleted(completedMessage(ORDER_ID, PAYMENT_ID, TRANSACTION_ID))
        consumer.handleCompensationEvent(cancelledMessage(ORDER_ID, PAYMENT_ID, TRANSACTION_ID))

        assertNoSales(stats())
    }

    @Test
    fun 취소가_결제_완료보다_먼저_와도_취소된_주문은_판매_통계에_남지_않는다() {
        deliverCancelBeforeCompleted(ORDER_ID, PAYMENT_ID, TRANSACTION_ID)

        assertNoSales(stats())
    }

    @Test
    fun 취소가_먼저_와도_같은_상품의_이전_판매는_줄지_않고_취소된_주문만_빠진다() {
        consumer.handlePaymentCompleted(completedMessage(EARLIER_ORDER_ID, EARLIER_PAYMENT_ID, EARLIER_TRANSACTION_ID))

        deliverCancelBeforeCompleted(ORDER_ID, PAYMENT_ID, TRANSACTION_ID)

        val stats = stats()
        assertThat(stats.orderCount).isEqualTo(1L)
        assertThat(stats.totalSalesQuantity).isEqualTo(2L)
        assertThat(stats.totalSalesAmount).isEqualByComparingTo("2000")
        assertThat(stats.netRevenue).isEqualByComparingTo("2000")
    }

    @Test
    fun 판매가_반영되기_전에_온_취소는_처리_완료로_남지_않는다() {
        val result = runCatching { consumer.handleCompensationEvent(cancelledMessage(ORDER_ID, PAYMENT_ID, TRANSACTION_ID)) }

        assertThat(result.isFailure).isTrue()
        assertThat(statisticsEventLogRepository.existsByEventId(cancelEventId(PAYMENT_ID))).isFalse()
    }

    @Test
    fun 취소가_이미_기록된_결제의_완료_이벤트는_판매에_더하지_않는다() {
        statisticsEventLogRepository.save(
            StatisticsEventLog(eventId = cancelEventId(PAYMENT_ID), eventType = "PaymentCancelled", orderId = ORDER_ID)
        )

        consumer.handlePaymentCompleted(completedMessage(ORDER_ID, PAYMENT_ID, TRANSACTION_ID))

        assertThat(productStatisticsRepository.findByProductId(productId)?.orderCount ?: 0L).isEqualTo(0L)
        assertThat(statisticsEventLogRepository.existsByEventId(TRANSACTION_ID)).isTrue()
    }

    private fun deliverCancelBeforeCompleted(orderId: Long, paymentId: Long, transactionId: String) {
        val cancel = cancelledMessage(orderId, paymentId, transactionId)
        val firstAttempt = runCatching { consumer.handleCompensationEvent(cancel) }

        consumer.handlePaymentCompleted(completedMessage(orderId, paymentId, transactionId))

        if (firstAttempt.isFailure) {
            consumer.handleCompensationEvent(cancel)
        }
    }

    private fun stats(): ProductStatistics = productStatisticsRepository.findByProductId(productId)!!

    private fun assertNoSales(stats: ProductStatistics) {
        assertThat(stats.orderCount).isEqualTo(0L)
        assertThat(stats.totalSalesQuantity).isEqualTo(0L)
        assertThat(stats.totalSalesAmount).isEqualByComparingTo("0")
        assertThat(stats.netRevenue).isEqualByComparingTo("0")
    }

    private fun completedMessage(orderId: Long, paymentId: Long, transactionId: String) =
        """{"paymentId":$paymentId,"orderId":$orderId,"userId":$BUYER_ID,"amount":3000,"pointAmount":0,"transactionId":"$transactionId","completedAt":"2026-11-13T07:57:44"}"""

    private fun cancelledMessage(orderId: Long, paymentId: Long, transactionId: String) =
        """{"eventType":"PaymentCancelled","eventId":"${cancelEventId(paymentId)}","paymentId":$paymentId,"orderId":$orderId,"userId":$BUYER_ID,"amount":3000,"transactionId":"$transactionId"}"""

    private fun cancelEventId(paymentId: Long) = "payment-cancelled-$paymentId"

    private fun createProduct(): Long {
        val categoryId = categoryCommandService.createCategory(CategoryCreateRequest(name = "통계-취소순서", parentCategoryId = null)).id
        return productCommandService.createProduct(
            ProductCreateRequest(
                sellerId = SELLER_ID,
                categoryId = categoryId,
                name = "통계 취소 순서 상품",
                description = "통계 취소 순서 설명",
                price = BigDecimal("1000"),
                imageUrls = listOf("a.jpg"),
                status = ProductStatus.AVAILABLE
            )
        ).id
    }

    companion object {
        private const val SELLER_ID = 9_401L
        private const val BUYER_ID = 9_402L
        private const val ORDER_ID = 9_403L
        private const val PAYMENT_ID = 9_404L
        private const val TRANSACTION_ID = "txn-9404"
        private const val EARLIER_ORDER_ID = 9_405L
        private const val EARLIER_PAYMENT_ID = 9_406L
        private const val EARLIER_TRANSACTION_ID = "txn-9406"
        private val STATISTICS_TABLES = listOf(
            "statistics_event_logs", "product_statistics", "wishlists", "reviews", "product_images", "products", "categories"
        )
    }
}
