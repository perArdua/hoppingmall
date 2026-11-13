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
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal

@DisplayName("통계 이벤트가 항목 중간에 실패한 뒤 다시 전달될 때의 상품 통계 MySQL 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class StatisticsRedeliveryMySqlIntegrationTest : MySqlIntegrationTestSupport() {

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

    private lateinit var failingService: FailOnceStatisticsCommandService

    private lateinit var consumer: StatisticsEventConsumer

    private var firstProductId: Long = 0

    private var secondProductId: Long = 0

    @BeforeEach
    fun setUp() {
        STATISTICS_TABLES.forEach { jdbcTemplate.update("DELETE FROM $it") }
        failingService = FailOnceStatisticsCommandService(productStatisticsCommandService)
        consumer = consumer()
        val categoryId = categoryCommandService.createCategory(CategoryCreateRequest(name = "통계-재전달", parentCategoryId = null)).id
        firstProductId = createProduct(categoryId, "통계 재전달 상품 1")
        secondProductId = createProduct(categoryId, "통계 재전달 상품 2")
        whenever(orderItemQueryPort.findByOrderId(ORDER_ID)).thenReturn(
            listOf(
                OrderItemInfo(id = 1L, orderId = ORDER_ID, productId = firstProductId, quantity = 3, totalPrice = BigDecimal("3000")),
                OrderItemInfo(id = 2L, orderId = ORDER_ID, productId = secondProductId, quantity = 1, totalPrice = BigDecimal("500"))
            )
        )
        whenever(orderItemQueryPort.findByOrderId(EARLIER_ORDER_ID)).thenReturn(
            listOf(OrderItemInfo(id = 3L, orderId = EARLIER_ORDER_ID, productId = firstProductId, quantity = 2, totalPrice = BigDecimal("2000")))
        )
    }

    @AfterEach
    fun tearDown() {
        STATISTICS_TABLES.forEach { jdbcTemplate.update("DELETE FROM $it") }
    }

    @Test
    fun 결제_완료가_두_번째_항목에서_실패한_뒤_다시_전달되어도_첫_항목_판매는_한_번만_더해진다() {
        failingService.failOnceFor(secondProductId)

        val firstAttempt = runCatching { consumer.handlePaymentCompleted(completedMessage(ORDER_ID, PAYMENT_ID, TRANSACTION_ID)) }
        consumer.handlePaymentCompleted(completedMessage(ORDER_ID, PAYMENT_ID, TRANSACTION_ID))

        assertThat(firstAttempt.isFailure).isTrue()
        val first = stats(firstProductId)
        assertThat(first.totalSalesQuantity).isEqualTo(3L)
        assertThat(first.totalSalesAmount).isEqualByComparingTo("3000")
        assertThat(first.orderCount).isEqualTo(1L)
        assertThat(stats(secondProductId).totalSalesQuantity).isEqualTo(1L)
    }

    @Test
    fun 결제_취소가_두_번째_항목에서_실패한_뒤_다시_전달되어도_첫_항목_판매는_한_번만_빠진다() {
        consumer.handlePaymentCompleted(completedMessage(EARLIER_ORDER_ID, EARLIER_PAYMENT_ID, EARLIER_TRANSACTION_ID))
        consumer.handlePaymentCompleted(completedMessage(ORDER_ID, PAYMENT_ID, TRANSACTION_ID))
        failingService.failOnceFor(secondProductId)

        val firstAttempt = runCatching { consumer.handleCompensationEvent(cancelledMessage(ORDER_ID, PAYMENT_ID, TRANSACTION_ID)) }
        consumer.handleCompensationEvent(cancelledMessage(ORDER_ID, PAYMENT_ID, TRANSACTION_ID))

        assertThat(firstAttempt.isFailure).isTrue()
        val first = stats(firstProductId)
        assertThat(first.totalSalesQuantity).isEqualTo(2L)
        assertThat(first.totalSalesAmount).isEqualByComparingTo("2000")
        assertThat(first.orderCount).isEqualTo(1L)
        assertThat(stats(secondProductId).totalSalesQuantity).isEqualTo(0L)
    }

    @Test
    fun 역보상이_두_번째_항목에서_실패한_뒤_다시_전달되어도_첫_항목_판매는_한_번만_빠진다() {
        consumer.handlePaymentCompleted(completedMessage(EARLIER_ORDER_ID, EARLIER_PAYMENT_ID, EARLIER_TRANSACTION_ID))
        consumer.handlePaymentCompleted(completedMessage(ORDER_ID, PAYMENT_ID, TRANSACTION_ID))
        failingService.failOnceFor(secondProductId)

        val firstAttempt = runCatching { consumer.handlePaymentReversal(reversalMessage(ORDER_ID, PAYMENT_ID)) }
        consumer.handlePaymentReversal(reversalMessage(ORDER_ID, PAYMENT_ID))

        assertThat(firstAttempt.isFailure).isTrue()
        val first = stats(firstProductId)
        assertThat(first.totalSalesQuantity).isEqualTo(2L)
        assertThat(first.totalSalesAmount).isEqualByComparingTo("2000")
        assertThat(first.orderCount).isEqualTo(1L)
        assertThat(stats(secondProductId).totalSalesQuantity).isEqualTo(0L)
    }

    private fun consumer(): StatisticsEventConsumer {
        val candidates = listOf(
            statisticsEventLogRepository, orderItemQueryPort, failingService, objectMapper, kafkaEventMapper, transactionTemplate
        )
        val constructor = StatisticsEventConsumer::class.java.constructors.single()
        val arguments = constructor.parameterTypes.map { type -> candidates.first { type.isInstance(it) } }
        return constructor.newInstance(*arguments.toTypedArray()) as StatisticsEventConsumer
    }

    private fun stats(productId: Long): ProductStatistics = productStatisticsRepository.findByProductId(productId)!!

    private fun completedMessage(orderId: Long, paymentId: Long, transactionId: String) =
        """{"paymentId":$paymentId,"orderId":$orderId,"userId":$BUYER_ID,"amount":3500,"pointAmount":0,"transactionId":"$transactionId","completedAt":"2026-11-13T07:57:44"}"""

    private fun cancelledMessage(orderId: Long, paymentId: Long, transactionId: String) =
        """{"eventType":"PaymentCancelled","eventId":"payment-cancelled-$paymentId","paymentId":$paymentId,"orderId":$orderId,"userId":$BUYER_ID,"amount":3500,"transactionId":"$transactionId"}"""

    private fun reversalMessage(orderId: Long, paymentId: Long) =
        """{"eventId":"reversal-$paymentId","paymentId":$paymentId,"orderId":$orderId,"userId":$BUYER_ID,"amount":"","transactionId":"","eventType":"PaymentReversalRequested"}"""

    private fun createProduct(categoryId: Long, name: String): Long =
        productCommandService.createProduct(
            ProductCreateRequest(
                sellerId = SELLER_ID,
                categoryId = categoryId,
                name = name,
                description = "$name 설명",
                price = BigDecimal("1000"),
                imageUrls = listOf("a.jpg"),
                status = ProductStatus.AVAILABLE
            )
        ).id

    class FailOnceStatisticsCommandService(
        private val delegate: ProductStatisticsCommandService
    ) : ProductStatisticsCommandService by delegate {

        @Volatile
        private var failingProductId: Long? = null

        fun failOnceFor(productId: Long) {
            failingProductId = productId
        }

        override fun incrementSalesStats(productId: Long, quantity: Long, amount: BigDecimal) {
            failIfTargeted(productId)
            delegate.incrementSalesStats(productId, quantity, amount)
        }

        override fun decrementSalesStats(productId: Long, quantity: Long, amount: BigDecimal) {
            failIfTargeted(productId)
            delegate.decrementSalesStats(productId, quantity, amount)
        }

        private fun failIfTargeted(productId: Long) {
            if (failingProductId == productId) {
                failingProductId = null
                throw IllegalStateException("통계 반영 중 장애: productId=$productId")
            }
        }
    }

    companion object {
        private const val SELLER_ID = 9_501L
        private const val BUYER_ID = 9_502L
        private const val ORDER_ID = 9_503L
        private const val PAYMENT_ID = 9_504L
        private const val TRANSACTION_ID = "txn-9504"
        private const val EARLIER_ORDER_ID = 9_505L
        private const val EARLIER_PAYMENT_ID = 9_506L
        private const val EARLIER_TRANSACTION_ID = "txn-9506"
        private val STATISTICS_TABLES = listOf(
            "statistics_event_logs", "product_statistics", "wishlists", "reviews", "product_images", "products", "categories"
        )
    }
}
