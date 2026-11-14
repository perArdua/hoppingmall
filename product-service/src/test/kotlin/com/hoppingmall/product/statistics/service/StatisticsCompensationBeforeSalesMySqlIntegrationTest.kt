package com.hoppingmall.product.statistics.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.hoppingmall.common.event.KafkaEventMapper
import com.hoppingmall.product.category.dto.request.CategoryCreateRequest
import com.hoppingmall.product.category.service.CategoryCommandService
import com.hoppingmall.product.common.enums.ProductStatus
import com.hoppingmall.product.product.domain.ProductStatistics
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
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.sql.Connection
import java.sql.Timestamp
import java.time.LocalDateTime

@DisplayName("판매 통계보다 먼저 온 역보상·취소 보상의 상품 통계 MySQL 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class StatisticsCompensationBeforeSalesMySqlIntegrationTest : MySqlIntegrationTestSupport() {

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
    }

    @AfterEach
    fun tearDown() {
        STATISTICS_TABLES.forEach { jdbcTemplate.update("DELETE FROM $it") }
    }

    @Test
    fun 판매_반영_전에_온_역보상은_주문_항목을_읽지_않고_기록만_남겨_처리를_끝낸다() {
        givenOrderDown()

        consumer.handlePaymentReversal(reversalMessage())

        assertThat(statisticsEventLogRepository.existsByEventId(REVERSAL_EVENT_ID)).isTrue()
        verify(orderItemQueryPort, never()).findByOrderId(any())
        assertNoSales(stats())
    }

    @Test
    fun 판매_반영_전에_온_역보상이_기록된_주문은_뒤에_온_결제_완료가_판매에_더하지_않는다() {
        givenOrderDown()
        consumer.handlePaymentReversal(reversalMessage())
        givenOrderUp()

        consumer.handlePaymentCompleted(completedMessage())

        assertThat(statisticsEventLogRepository.existsByEventId(TRANSACTION_ID)).isTrue()
        assertNoSales(stats())
    }

    @Test
    fun 판매_반영_전에_온_취소_보상은_주문_항목을_읽지_않고_기록만_남겨_처리를_끝내고_뒤에_온_결제_완료는_판매에_더하지_않는다() {
        givenOrderDown()

        consumer.handleCompensationEvent(cancelledMessage())

        assertThat(statisticsEventLogRepository.existsByEventId(CANCEL_EVENT_ID)).isTrue()
        verify(orderItemQueryPort, never()).findByOrderId(any())

        givenOrderUp()
        consumer.handlePaymentCompleted(completedMessage())

        assertThat(statisticsEventLogRepository.existsByEventId(TRANSACTION_ID)).isTrue()
        assertNoSales(stats())
    }

    @Test
    fun 결제_완료_기록이_커밋되기_전에_온_역보상은_그_기록을_기다렸다가_판매를_뺀다() {
        givenOrderUp()
        productStatisticsCommandService.incrementSalesStats(productId, QUANTITY, AMOUNT)

        val result = runWhileHolding(
            holdingChanges = { insertLog(it, TRANSACTION_ID, COMPLETED) },
            actionName = "결제 완료 전 역보상",
            waitTable = "statistics_event_logs"
        ) {
            consumer.handlePaymentReversal(reversalMessage())
        }

        assertThat(result.isSuccess).isTrue()
        assertThat(statisticsEventLogRepository.existsByEventId(REVERSAL_EVENT_ID)).isTrue()
        assertNoSales(stats())
    }

    @Test
    fun 판매_반영_전_역보상_기록이_커밋되기_전에_온_결제_완료는_그_기록을_기다렸다가_판매에_더하지_않는다() {
        givenOrderUp()

        val result = runWhileHolding(
            holdingChanges = {
                lockCompletedLogs(it)
                insertLog(it, REVERSAL_EVENT_ID, REVERSAL)
            },
            actionName = "역보상 기록 전 결제 완료",
            waitTable = "statistics_event_logs"
        ) {
            consumer.handlePaymentCompleted(completedMessage())
        }

        assertThat(result.isSuccess).isTrue()
        assertThat(statisticsEventLogRepository.existsByEventId(TRANSACTION_ID)).isTrue()
        assertNoSales(stats())
    }

    @Test
    fun 판매_반영_전_취소_보상_기록이_커밋되기_전에_온_결제_완료는_그_기록을_기다렸다가_판매에_더하지_않는다() {
        givenOrderUp()

        val result = runWhileHolding(
            holdingChanges = {
                lockCompletedLogs(it)
                insertLog(it, CANCEL_EVENT_ID, CANCELLED)
            },
            actionName = "취소 보상 기록 전 결제 완료",
            waitTable = "statistics_event_logs"
        ) {
            consumer.handlePaymentCompleted(completedMessage())
        }

        assertThat(result.isSuccess).isTrue()
        assertNoSales(stats())
    }

    private fun lockCompletedLogs(connection: Connection) {
        connection.prepareStatement(
            "SELECT id FROM statistics_event_logs WHERE order_id = ? AND event_type IN (?) FOR UPDATE"
        ).use {
            it.setLong(1, ORDER_ID)
            it.setString(2, COMPLETED)
            it.executeQuery().close()
        }
    }

    private fun insertLog(connection: Connection, eventId: String, eventType: String) {
        connection.prepareStatement(
            "INSERT INTO statistics_event_logs (event_id, event_type, order_id, created_at, updated_at) VALUES (?, ?, ?, ?, ?)"
        ).use {
            val now = Timestamp.valueOf(LocalDateTime.now())
            it.setString(1, eventId)
            it.setString(2, eventType)
            it.setLong(3, ORDER_ID)
            it.setTimestamp(4, now)
            it.setTimestamp(5, now)
            it.executeUpdate()
        }
    }

    private fun givenOrderDown() {
        doThrow(IllegalStateException("order 다운")).whenever(orderItemQueryPort).findByOrderId(ORDER_ID)
    }

    private fun givenOrderUp() {
        doReturn(
            listOf(OrderItemInfo(id = 1L, orderId = ORDER_ID, productId = productId, quantity = QUANTITY.toInt(), totalPrice = AMOUNT))
        ).whenever(orderItemQueryPort).findByOrderId(ORDER_ID)
    }

    private fun stats(): ProductStatistics = productStatisticsRepository.findByProductId(productId)!!

    private fun assertNoSales(stats: ProductStatistics) {
        assertThat(stats.orderCount).isEqualTo(0L)
        assertThat(stats.totalSalesQuantity).isEqualTo(0L)
        assertThat(stats.totalSalesAmount).isEqualByComparingTo("0")
        assertThat(stats.netRevenue).isEqualByComparingTo("0")
    }

    private fun completedMessage() =
        """{"paymentId":$PAYMENT_ID,"orderId":$ORDER_ID,"userId":$BUYER_ID,"amount":3000,"pointAmount":0,"transactionId":"$TRANSACTION_ID","completedAt":"2026-11-15T01:00:00"}"""

    private fun reversalMessage() =
        """{"eventId":"$REVERSAL_EVENT_ID","paymentId":$PAYMENT_ID,"orderId":$ORDER_ID,"userId":$BUYER_ID,"amount":"3000","transactionId":"$TRANSACTION_ID","eventType":"$REVERSAL"}"""

    private fun cancelledMessage() =
        """{"eventType":"$CANCELLED","eventId":"$CANCEL_EVENT_ID","paymentId":$PAYMENT_ID,"orderId":$ORDER_ID,"userId":$BUYER_ID,"amount":3000,"transactionId":"$TRANSACTION_ID"}"""

    private fun createProduct(): Long {
        val categoryId = categoryCommandService.createCategory(CategoryCreateRequest(name = "통계-판매전보상", parentCategoryId = null)).id
        return productCommandService.createProduct(
            ProductCreateRequest(
                sellerId = SELLER_ID,
                categoryId = categoryId,
                name = "통계 판매 전 보상 상품",
                description = "통계 판매 전 보상 설명",
                price = BigDecimal("1000"),
                imageUrls = listOf("a.jpg"),
                status = ProductStatus.AVAILABLE
            )
        ).id
    }

    companion object {
        private const val SELLER_ID = 9_701L
        private const val BUYER_ID = 9_702L
        private const val ORDER_ID = 9_703L
        private const val PAYMENT_ID = 9_704L
        private const val TRANSACTION_ID = "txn-9704"
        private const val REVERSAL_EVENT_ID = "timeout-payment-completed-txn-9704"
        private const val CANCEL_EVENT_ID = "payment-cancelled-9704"
        private const val COMPLETED = "PaymentCompleted"
        private const val REVERSAL = "PaymentReversalRequested"
        private const val CANCELLED = "PaymentCancelled"
        private const val QUANTITY = 3L
        private val AMOUNT = BigDecimal("3000")
        private val STATISTICS_TABLES = listOf(
            "statistics_event_logs", "product_statistics", "wishlists", "reviews", "product_images", "products", "categories"
        )
    }
}
