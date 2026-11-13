package com.hoppingmall.order.refund.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.hoppingmall.common.KafkaTopics
import com.hoppingmall.common.event.AvroEventConverter
import com.hoppingmall.common.event.AvroJsonDeserializer
import com.hoppingmall.order.order.domain.Order
import com.hoppingmall.order.order.domain.OrderItem
import com.hoppingmall.order.order.domain.repository.OrderItemRepository
import com.hoppingmall.order.order.domain.repository.OrderRepository
import com.hoppingmall.order.port.PaymentInfo
import com.hoppingmall.order.port.PaymentQueryPort
import com.hoppingmall.order.refund.dto.request.RefundCreateRequest
import com.hoppingmall.order.refund.dto.request.RefundItemRequest
import com.hoppingmall.order.refund.dto.response.RefundResponse
import com.hoppingmall.order.refund.enum.RefundReason
import com.hoppingmall.order.refund.enum.RefundStatus
import com.hoppingmall.order.shipping.domain.Shipping
import com.hoppingmall.order.shipping.domain.repository.ShippingRepository
import com.hoppingmall.order.support.MySqlJpaTestSupport
import com.hoppingmall.outbox.service.TransactionalEventPublisher
import io.confluent.kafka.schemaregistry.testutil.MockSchemaRegistry
import io.confluent.kafka.serializers.KafkaAvroSerializer
import io.confluent.kafka.serializers.subject.TopicRecordNameStrategy
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.math.BigDecimal

@DisplayName("환불 완료 이벤트 메시지의 환불 항목·쿠폰 MySQL 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@Import(RefundCommandServiceImpl::class, KafkaRefundEventPublisher::class)
class RefundCompletedEventMessageMySqlIntegrationTest : MySqlJpaTestSupport() {

    @Autowired
    private lateinit var refundCommandService: RefundCommandServiceImpl

    @Autowired
    private lateinit var orderRepository: OrderRepository

    @Autowired
    private lateinit var orderItemRepository: OrderItemRepository

    @Autowired
    private lateinit var shippingRepository: ShippingRepository

    @MockitoBean
    private lateinit var paymentQueryPort: PaymentQueryPort

    @MockitoBean
    private lateinit var transactionalEventPublisher: TransactionalEventPublisher

    private val objectMapper = ObjectMapper()

    @BeforeEach
    fun setUp() {
        jdbcTemplate.update("DELETE FROM refund_items")
        jdbcTemplate.update("DELETE FROM refunds")
        jdbcTemplate.update("DELETE FROM shippings")
    }

    @AfterEach
    fun tearDown() {
        MockSchemaRegistry.dropScope(SCHEMA_SCOPE)
    }

    @Test
    fun 쿠폰을_쓴_주문을_전체_환불하면_메시지에_환불_항목과_쿠폰이_실린다() {
        val (orderId, itemIds) = saveOrder(BigDecimal("10000") to 1, BigDecimal("20000") to 2)
        givenPayment(orderId, paidAmount = BigDecimal("47000"), couponId = COUPON_ID)

        refund(orderId, itemIds[0] to 1, itemIds[1] to 2)

        val message = publishedMessages(1).single()
        assertThat(message.get("isFullRefund").asBoolean()).isTrue()
        assertThat(message.get("items").map { it.get("productId").asLong() to it.get("quantity").asInt() })
            .containsExactlyInAnyOrder(100L to 1, 101L to 2)
        assertThat(refundPriceSum(message)).isEqualByComparingTo("47000")
        assertThat(message.get("couponId").isNull).isFalse()
        assertThat(message.get("couponId").asLong()).isEqualTo(COUPON_ID)
    }

    @Test
    fun 쿠폰을_쓴_주문을_부분_환불하면_메시지에_환불_항목만_실리고_쿠폰은_비어_있다() {
        val (orderId, itemIds) = saveOrder(BigDecimal("10000") to 1, BigDecimal("20000") to 2)
        givenPayment(orderId, paidAmount = BigDecimal("47000"), couponId = COUPON_ID)

        val response = refund(orderId, itemIds[1] to 1)

        val message = publishedMessages(1).single()
        assertThat(message.get("isFullRefund").asBoolean()).isFalse()
        assertThat(message.get("items").map { it.get("productId").asLong() to it.get("quantity").asInt() })
            .containsExactly(101L to 1)
        assertThat(refundPriceSum(message)).isEqualByComparingTo(response.refundAmount)
        assertThat(message.get("couponId").isNull).isTrue()
    }

    @Test
    fun 판매자가_승인한_전체_환불도_메시지에_환불_항목과_쿠폰이_실린다() {
        val (orderId, itemIds) = saveOrder(BigDecimal("10000") to 2)
        val payment = givenPayment(orderId, paidAmount = BigDecimal("18000"), couponId = COUPON_ID)
        whenever(paymentQueryPort.findById(PAYMENT_ID)).thenReturn(payment)
        shippingRepository.saveAndFlush(
            Shipping.create(
                orderId = orderId, buyerId = BUYER_ID, carrierName = "택배", trackingNumber = "T-1",
                recipientName = "구매자", recipientPhone = "010-0000-0000", recipientAddress = "주소"
            )
        )
        jdbcTemplate.update("UPDATE shippings SET status = 'IN_TRANSIT' WHERE order_id = ?", orderId)
        val requested = refund(orderId, itemIds[0] to 2)
        assertThat(requested.status).isEqualTo(RefundStatus.REQUESTED)
        verify(transactionalEventPublisher, never()).publishEvent(any(), any(), any(), any(), any(), anyOrNull())

        refundCommandService.approveRefund(requested.id, SELLER_ID)

        val message = publishedMessages(1).single()
        assertThat(message.get("items").map { it.get("productId").asLong() to it.get("quantity").asInt() })
            .containsExactly(100L to 2)
        assertThat(refundPriceSum(message)).isEqualByComparingTo("18000")
        assertThat(message.get("couponId").asLong()).isEqualTo(COUPON_ID)
    }

    private fun refund(orderId: Long, vararg items: Pair<Long, Int>): RefundResponse =
        refundCommandService.requestRefund(
            BUYER_ID,
            RefundCreateRequest(
                orderId = orderId,
                reason = RefundReason.CHANGE_OF_MIND,
                items = items.map { (orderItemId, quantity) -> RefundItemRequest(orderItemId, quantity) }
            )
        )

    private fun publishedMessages(count: Int): List<JsonNode> {
        val eventTypes = argumentCaptor<String>()
        val eventData = argumentCaptor<Any>()
        verify(transactionalEventPublisher, times(count)).publishEvent(
            any(), any(), eventTypes.capture(), eventData.capture(), eq(KafkaTopics.REFUND_COMPLETION), anyOrNull()
        )
        return eventTypes.allValues.zip(eventData.allValues).map { (eventType, data) ->
            objectMapper.readTree(roundTrip(eventType, objectMapper.writeValueAsString(data)))
        }
    }

    private fun roundTrip(eventType: String, eventData: String): String {
        val record = AvroEventConverter(objectMapper).convertJsonToAvro(eventType, eventData)
        val config = mapOf(
            "schema.registry.url" to "mock://$SCHEMA_SCOPE",
            "value.subject.name.strategy" to TopicRecordNameStrategy::class.java.name
        )
        val serializer = KafkaAvroSerializer().apply { configure(config, false) }
        val deserializer = AvroJsonDeserializer().apply { configure(config, false) }
        return deserializer.deserialize(
            KafkaTopics.REFUND_COMPLETION,
            serializer.serialize(KafkaTopics.REFUND_COMPLETION, record)
        )!!
    }

    private fun refundPriceSum(message: JsonNode): BigDecimal =
        message.get("items").map { BigDecimal(it.get("refundPrice").asText()) }.fold(BigDecimal.ZERO, BigDecimal::add)

    private fun givenPayment(orderId: Long, paidAmount: BigDecimal, couponId: Long?): PaymentInfo {
        val payment = PaymentInfo(
            id = PAYMENT_ID,
            orderId = orderId,
            amount = paidAmount,
            pointAmount = BigDecimal.ZERO,
            couponId = couponId,
            status = "SUCCESS"
        )
        whenever(paymentQueryPort.findByOrderId(orderId)).thenReturn(payment)
        return payment
    }

    private fun saveOrder(vararg lines: Pair<BigDecimal, Int>): Pair<Long, List<Long>> {
        val totalAmount = lines.fold(BigDecimal.ZERO) { acc, (price, quantity) -> acc.add(price.multiply(BigDecimal(quantity))) }
        val orderId = orderRepository.saveAndFlush(Order.create(buyerId = BUYER_ID, totalAmount = totalAmount)).id!!
        val itemIds = lines.mapIndexed { index, (price, quantity) ->
            orderItemRepository.saveAndFlush(
                OrderItem.create(
                    orderId = orderId, sellerId = SELLER_ID, productId = 100L + index,
                    productName = "상품$index", productPrice = price, quantity = quantity
                )
            ).id!!
        }
        jdbcTemplate.update("UPDATE orders SET status = 'PAID' WHERE id = ?", orderId)
        return orderId to itemIds
    }

    companion object {
        private const val BUYER_ID = 10L
        private const val SELLER_ID = 5L
        private const val PAYMENT_ID = 900L
        private const val COUPON_ID = 77L
        private const val SCHEMA_SCOPE = "refund-message"
    }
}
