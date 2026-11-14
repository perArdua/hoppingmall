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
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.PageRequest
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.math.BigDecimal

@DisplayName("판매 통계 DLQ보다 먼저 재처리되는 역보상·취소 보상 DLQ MySQL 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@TestPropertySource(properties = ["dlq.scheduler.enabled=false"])
class StatisticsCompensationBeforeSalesDLQMySqlIntegrationTest : MySqlIntegrationTestSupport() {

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
    }

    @AfterEach
    fun tearDown() {
        TABLES.forEach { jdbcTemplate.update("DELETE FROM $it") }
    }

    @Test
    fun order가_오래_다운돼_역보상_DLQ가_판매_통계_DLQ보다_먼저_세_번_재처리돼도_실패로_끝나지_않고_판매가_남지_않는다() {
        val reversal = deadLetter(KafkaTopics.PAYMENT_REVERSAL, COMPENSATION_GROUP, 1L, reversalMessage())
        val completed = deadLetter(KafkaTopics.PAYMENT, SALES_GROUP, 2L, completedMessage())
        givenOrderDown()

        repeat(DLQCommandService.MAX_RETRY_COUNT) { retryIfPending(reversal) }

        givenOrderUp()
        retryIfPending(completed)

        assertThat(dlq(reversal).status).isEqualTo(DLQStatus.PROCESSED)
        assertThat(dlq(completed).status).isEqualTo(DLQStatus.PROCESSED)
        assertNoSales(stats())
        verifyNoInteractions(dlqMessagePublisher)
    }

    @Test
    fun order가_오래_다운돼_취소_보상_DLQ가_판매_통계_DLQ보다_먼저_세_번_재처리돼도_실패로_끝나지_않고_판매가_남지_않는다() {
        val cancelled = deadLetter(KafkaTopics.PAYMENT_COMPENSATION, COMPENSATION_GROUP, 1L, cancelledMessage())
        val completed = deadLetter(KafkaTopics.PAYMENT, SALES_GROUP, 2L, completedMessage())
        givenOrderDown()

        repeat(DLQCommandService.MAX_RETRY_COUNT) { retryIfPending(cancelled) }

        givenOrderUp()
        retryIfPending(completed)

        assertThat(dlq(cancelled).status).isEqualTo(DLQStatus.PROCESSED)
        assertThat(dlq(completed).status).isEqualTo(DLQStatus.PROCESSED)
        assertNoSales(stats())
        verifyNoInteractions(dlqMessagePublisher)
    }

    private fun retryIfPending(id: Long) {
        jdbcTemplate.update("UPDATE dlq_messages SET next_retry_at = ? WHERE id = ?", System.currentTimeMillis() - 10_000L, id)
        dlqMessageRepository.findAutoRetryableMessages(
            DLQStatus.PENDING, DLQCommandService.MAX_RETRY_COUNT, System.currentTimeMillis(), PageRequest.of(0, 20)
        ).content.filter { it.id == id }.forEach { dlqCommandService.retryDLQMessage(it.id!!) }
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

    private fun givenOrderDown() {
        doThrow(IllegalStateException("order 다운")).whenever(orderItemQueryPort).findByOrderId(ORDER_ID)
    }

    private fun givenOrderUp() {
        doReturn(
            listOf(OrderItemInfo(id = 1L, orderId = ORDER_ID, productId = productId, quantity = 3, totalPrice = BigDecimal("3000")))
        ).whenever(orderItemQueryPort).findByOrderId(ORDER_ID)
    }

    private fun dlq(id: Long): DLQMessage = dlqMessageRepository.findById(id).orElseThrow()

    private fun stats(): ProductStatistics? = productStatisticsRepository.findByProductId(productId)

    private fun assertNoSales(stats: ProductStatistics?) {
        if (stats == null) return
        assertThat(stats.orderCount).isEqualTo(0L)
        assertThat(stats.totalSalesQuantity).isEqualTo(0L)
        assertThat(stats.totalSalesAmount).isEqualByComparingTo("0")
        assertThat(stats.netRevenue).isEqualByComparingTo("0")
    }

    private fun completedMessage() =
        """{"paymentId":$PAYMENT_ID,"orderId":$ORDER_ID,"userId":$BUYER_ID,"amount":3000,"pointAmount":0,"transactionId":"$TRANSACTION_ID","completedAt":"2026-11-15T01:00:00"}"""

    private fun reversalMessage() =
        """{"eventId":"timeout-payment-completed-$TRANSACTION_ID","paymentId":$PAYMENT_ID,"orderId":$ORDER_ID,"userId":$BUYER_ID,"amount":"3000","transactionId":"$TRANSACTION_ID","eventType":"PaymentReversalRequested"}"""

    private fun cancelledMessage() =
        """{"eventType":"PaymentCancelled","eventId":"payment-cancelled-$PAYMENT_ID","paymentId":$PAYMENT_ID,"orderId":$ORDER_ID,"userId":$BUYER_ID,"amount":3000,"transactionId":"$TRANSACTION_ID"}"""

    private fun createProduct(): Long {
        val categoryId = categoryCommandService.createCategory(CategoryCreateRequest(name = "통계-판매전보상DLQ", parentCategoryId = null)).id
        return productCommandService.createProduct(
            ProductCreateRequest(
                sellerId = SELLER_ID,
                categoryId = categoryId,
                name = "통계 판매 전 보상 DLQ 상품",
                description = "통계 판매 전 보상 DLQ 설명",
                price = BigDecimal("1000"),
                imageUrls = listOf("a.jpg"),
                status = ProductStatus.AVAILABLE
            )
        ).id
    }

    companion object {
        private const val SALES_GROUP = "statistics-service"
        private const val COMPENSATION_GROUP = "statistics-compensation-service"
        private const val SELLER_ID = 9_801L
        private const val BUYER_ID = 9_802L
        private const val ORDER_ID = 9_803L
        private const val PAYMENT_ID = 9_804L
        private const val TRANSACTION_ID = "txn-9804"
        private val TABLES = listOf(
            "dlq_messages", "statistics_event_logs", "product_statistics", "wishlists", "reviews", "product_images", "products", "categories"
        )
    }
}
