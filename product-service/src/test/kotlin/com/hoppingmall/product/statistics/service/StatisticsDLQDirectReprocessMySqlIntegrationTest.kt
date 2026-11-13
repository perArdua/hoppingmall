package com.hoppingmall.product.statistics.service

import com.hoppingmall.common.KafkaTopics
import com.hoppingmall.dlq.domain.DLQMessage
import com.hoppingmall.dlq.domain.DLQStatus
import com.hoppingmall.dlq.domain.DeadLetterMessage
import com.hoppingmall.dlq.domain.repository.DLQMessageRepository
import com.hoppingmall.dlq.publisher.DLQMessagePublisher
import com.hoppingmall.dlq.service.DLQCommandService
import com.hoppingmall.product.category.dto.request.CategoryCreateRequest
import com.hoppingmall.product.category.service.CategoryCommandService
import com.hoppingmall.product.common.enums.ProductStatus
import com.hoppingmall.product.product.domain.ProductStatistics
import com.hoppingmall.product.product.domain.repository.ProductStatisticsRepository
import com.hoppingmall.product.product.dto.request.ProductCreateRequest
import com.hoppingmall.product.product.service.ProductCommandService
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
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.PageRequest
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.math.BigDecimal

@DisplayName("상품 통계 DLQ 메시지 직접 재처리 순서 MySQL 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@TestPropertySource(properties = ["dlq.scheduler.enabled=false"])
class StatisticsDLQDirectReprocessMySqlIntegrationTest : MySqlIntegrationTestSupport() {

    @MockitoBean
    private lateinit var orderItemQueryPort: OrderItemQueryPort

    @MockitoBean
    private lateinit var dlqMessagePublisher: DLQMessagePublisher

    @Autowired
    private lateinit var dlqCommandService: DLQCommandService

    @Autowired
    private lateinit var dlqMessageRepository: DLQMessageRepository

    @Autowired
    private lateinit var productStatisticsRepository: ProductStatisticsRepository

    @Autowired
    private lateinit var productCommandService: ProductCommandService

    @Autowired
    private lateinit var categoryCommandService: CategoryCommandService

    private var productId: Long = 0

    @BeforeEach
    fun setUp() {
        TABLES.forEach { jdbcTemplate.update("DELETE FROM $it") }
        productId = createProduct()
        whenever(orderItemQueryPort.findByOrderId(ORDER_ID)).thenReturn(
            listOf(OrderItemInfo(id = 1L, orderId = ORDER_ID, productId = productId, quantity = 3, totalPrice = BigDecimal("3000")))
        )
    }

    @AfterEach
    fun tearDown() {
        TABLES.forEach { jdbcTemplate.update("DELETE FROM $it") }
    }

    @Test
    fun 역보상_DLQ가_판매_통계_DLQ보다_먼저_재처리되면_한_번_미뤄졌다가_다음_차례에_판매를_되돌린다() {
        val reversal = deadLetter(KafkaTopics.PAYMENT_REVERSAL, REVERSAL_GROUP, 1L, reversalMessage())
        val completed = deadLetter(KafkaTopics.PAYMENT, SALES_GROUP, 2L, completedMessage())
        makeDue(reversal, completed)

        retryDueMessages()

        assertThat(dlq(reversal).status).isEqualTo(DLQStatus.PENDING)
        assertThat(dlq(reversal).retryCount).isEqualTo(1)
        assertThat(dlq(completed).status).isEqualTo(DLQStatus.PROCESSED)
        assertThat(stats().orderCount).isEqualTo(1L)

        makeDue(reversal)
        retryDueMessages()

        assertThat(dlq(reversal).status).isEqualTo(DLQStatus.PROCESSED)
        assertNoSales(stats())
        verifyNoInteractions(dlqMessagePublisher)
    }

    @Test
    fun 취소_보상_DLQ가_판매_통계_DLQ보다_먼저_재처리되면_한_번_미뤄졌다가_다음_차례에_판매를_되돌린다() {
        val cancelled = deadLetter(KafkaTopics.PAYMENT_COMPENSATION, REVERSAL_GROUP, 1L, cancelledMessage())
        val completed = deadLetter(KafkaTopics.PAYMENT, SALES_GROUP, 2L, completedMessage())
        makeDue(cancelled, completed)

        retryDueMessages()

        assertThat(dlq(cancelled).status).isEqualTo(DLQStatus.PENDING)
        assertThat(dlq(completed).status).isEqualTo(DLQStatus.PROCESSED)

        makeDue(cancelled)
        retryDueMessages()

        assertThat(dlq(cancelled).status).isEqualTo(DLQStatus.PROCESSED)
        assertNoSales(stats())
        verifyNoInteractions(dlqMessagePublisher)
    }

    @Test
    fun 판매_통계_DLQ가_먼저_재처리되면_같은_차례에서_역보상까지_끝난다() {
        val completed = deadLetter(KafkaTopics.PAYMENT, SALES_GROUP, 1L, completedMessage())
        val reversal = deadLetter(KafkaTopics.PAYMENT_REVERSAL, REVERSAL_GROUP, 2L, reversalMessage())
        makeDue(completed, reversal)

        retryDueMessages()

        assertThat(dlq(completed).status).isEqualTo(DLQStatus.PROCESSED)
        assertThat(dlq(reversal).status).isEqualTo(DLQStatus.PROCESSED)
        assertNoSales(stats())
        verifyNoInteractions(dlqMessagePublisher)
    }

    private fun deadLetter(topic: String, group: String, offset: Long, value: String): Long {
        dlqCommandService.saveDLQMessage(
            DeadLetterMessage(
                originalTopic = topic,
                originalPartition = 0,
                originalOffset = offset,
                originalKey = ORDER_ID.toString(),
                originalValue = value,
                exception = "java.lang.IllegalStateException: 재시도 소진",
                timestamp = System.currentTimeMillis(),
                consumerGroup = group
            )
        )
        return jdbcTemplate.queryForObject(
            "SELECT id FROM dlq_messages WHERE original_topic = ? AND original_offset = ?",
            Long::class.java,
            topic,
            offset
        )!!
    }

    private fun makeDue(vararg ids: Long) {
        val base = System.currentTimeMillis() - 10_000L
        ids.forEachIndexed { index, id ->
            jdbcTemplate.update("UPDATE dlq_messages SET next_retry_at = ? WHERE id = ?", base + index, id)
        }
    }

    private fun retryDueMessages() {
        dlqMessageRepository.findAutoRetryableMessages(
            DLQStatus.PENDING, DLQCommandService.MAX_RETRY_COUNT, System.currentTimeMillis(), PageRequest.of(0, 20)
        ).content.forEach { dlqCommandService.retryDLQMessage(it.id!!) }
    }

    private fun dlq(id: Long): DLQMessage = dlqMessageRepository.findById(id).orElseThrow()

    private fun stats(): ProductStatistics = productStatisticsRepository.findByProductId(productId)!!

    private fun assertNoSales(stats: ProductStatistics) {
        assertThat(stats.orderCount).isEqualTo(0L)
        assertThat(stats.totalSalesQuantity).isEqualTo(0L)
        assertThat(stats.totalSalesAmount).isEqualByComparingTo("0")
        assertThat(stats.netRevenue).isEqualByComparingTo("0")
    }

    private fun completedMessage() =
        """{"paymentId":$PAYMENT_ID,"orderId":$ORDER_ID,"userId":$BUYER_ID,"amount":3000,"pointAmount":0,"transactionId":"$TRANSACTION_ID","completedAt":"2026-11-14T05:40:00"}"""

    private fun reversalMessage() =
        """{"eventId":"timeout-payment-completed-$TRANSACTION_ID","paymentId":$PAYMENT_ID,"orderId":$ORDER_ID,"userId":$BUYER_ID,"amount":"3000","transactionId":"$TRANSACTION_ID","eventType":"PaymentReversalRequested"}"""

    private fun cancelledMessage() =
        """{"eventType":"PaymentCancelled","eventId":"payment-cancelled-$PAYMENT_ID","paymentId":$PAYMENT_ID,"orderId":$ORDER_ID,"userId":$BUYER_ID,"amount":3000,"transactionId":"$TRANSACTION_ID"}"""

    private fun createProduct(): Long {
        val categoryId = categoryCommandService.createCategory(CategoryCreateRequest(name = "통계-DLQ재처리", parentCategoryId = null)).id
        return productCommandService.createProduct(
            ProductCreateRequest(
                sellerId = SELLER_ID,
                categoryId = categoryId,
                name = "통계 DLQ 재처리 상품",
                description = "통계 DLQ 재처리 설명",
                price = BigDecimal("1000"),
                imageUrls = listOf("a.jpg"),
                status = ProductStatus.AVAILABLE
            )
        ).id
    }

    companion object {
        private const val SALES_GROUP = "statistics-service"
        private const val REVERSAL_GROUP = "statistics-compensation-service"
        private const val SELLER_ID = 9_601L
        private const val BUYER_ID = 9_602L
        private const val ORDER_ID = 9_603L
        private const val PAYMENT_ID = 9_604L
        private const val TRANSACTION_ID = "txn-9604"
        private val TABLES = listOf(
            "dlq_messages", "statistics_event_logs", "product_statistics", "wishlists", "reviews", "product_images", "products", "categories"
        )
    }
}
