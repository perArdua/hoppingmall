package com.hoppingmall.common.event

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test

@DisplayName("AvroEventConverter 단위 테스트")
@DisplayNameGeneration(ReplaceUnderscores::class)
class AvroEventConverterTest {

    private val objectMapper = ObjectMapper()
    private val converter = AvroEventConverter(objectMapper)

    @Test
    fun PaymentCompletedEvent_JSON을_Avro_레코드로_변환한다() {
        val json = objectMapper.writeValueAsString(
            mapOf(
                "paymentId" to 1,
                "orderId" to 10,
                "userId" to 100,
                "amount" to "50000",
                "pointAmount" to "500",
                "method" to "CREDIT_CARD",
                "status" to "SUCCESS",
                "transactionId" to "tx-001",
                "completedAt" to "2026-01-01T00:00:00"
            )
        )

        val record = converter.convertJsonToAvro("PaymentCompletedEvent", json)

        assertThat(record.get("paymentId")).isEqualTo(1L)
        assertThat(record.get("orderId")).isEqualTo(10L)
        assertThat(record.get("userId")).isEqualTo(100L)
        assertThat(record.get("amount").toString()).isEqualTo("50000")
        assertThat(record.get("transactionId").toString()).isEqualTo("tx-001")
    }

    @Test
    fun PaymentCompleted_별칭으로도_변환이_가능하다() {
        val json = objectMapper.writeValueAsString(
            mapOf(
                "paymentId" to 1,
                "orderId" to 10,
                "userId" to 100,
                "amount" to "50000",
                "pointAmount" to "500",
                "method" to "CREDIT_CARD",
                "status" to "SUCCESS",
                "transactionId" to "tx-001",
                "completedAt" to "2026-01-01T00:00:00"
            )
        )

        val record = converter.convertJsonToAvro("PaymentCompleted", json)

        assertThat(record.get("paymentId")).isEqualTo(1L)
    }

    @Test
    fun PaymentFailedEvent_JSON을_Avro_레코드로_변환한다() {
        val json = objectMapper.writeValueAsString(
            mapOf(
                "eventId" to "evt-001",
                "paymentId" to 1,
                "orderId" to 10,
                "userId" to 100,
                "amount" to "30000",
                "reason" to "잔액 부족"
            )
        )

        val record = converter.convertJsonToAvro("PaymentFailedEvent", json)

        assertThat(record.get("eventId").toString()).isEqualTo("evt-001")
        assertThat(record.get("reason").toString()).isEqualTo("잔액 부족")
    }

    @Test
    fun PaymentCancelledEvent_JSON을_Avro_레코드로_변환한다() {
        val json = objectMapper.writeValueAsString(
            mapOf(
                "eventId" to "evt-002",
                "paymentId" to 2,
                "orderId" to 20,
                "userId" to 200,
                "amount" to "10000",
                "transactionId" to "tx-002"
            )
        )

        val record = converter.convertJsonToAvro("PaymentCancelledEvent", json)

        assertThat(record.get("eventId").toString()).isEqualTo("evt-002")
        assertThat(record.get("paymentId")).isEqualTo(2L)
    }

    @Test
    fun PointEarnRequestEvent_JSON을_Avro_레코드로_변환한다() {
        val json = objectMapper.writeValueAsString(
            mapOf(
                "eventId" to "evt-003",
                "userId" to 100,
                "orderId" to 10,
                "paymentId" to 1,
                "earnAmount" to "500",
                "reason" to "결제 완료"
            )
        )

        val record = converter.convertJsonToAvro("PointEarnRequestEvent", json)

        assertThat(record.get("earnAmount").toString()).isEqualTo("500")
        assertThat(record.get("reason").toString()).isEqualTo("결제 완료")
    }

    @Test
    fun MembershipUpdateRequestEvent_JSON을_Avro_레코드로_변환한다() {
        val json = objectMapper.writeValueAsString(
            mapOf(
                "eventId" to "evt-004",
                "userId" to 100,
                "orderId" to 10,
                "paymentId" to 1,
                "amount" to "50000"
            )
        )

        val record = converter.convertJsonToAvro("MembershipUpdateRequestEvent", json)

        assertThat(record.get("amount").toString()).isEqualTo("50000")
    }

    @Test
    fun NotificationEvent_JSON을_Avro_레코드로_변환한다() {
        val json = objectMapper.writeValueAsString(
            mapOf(
                "eventId" to "evt-005",
                "userId" to 100,
                "type" to "PAYMENT_COMPLETED",
                "title" to "결제 완료",
                "content" to "결제가 완료되었습니다.",
                "metadata" to null
            )
        )

        val record = converter.convertJsonToAvro("NotificationEvent", json)

        assertThat(record.get("title").toString()).isEqualTo("결제 완료")
        assertThat(record.get("metadata")).isNull()
    }

    @Test
    fun NotificationRequested_접미사_이벤트는_NotificationEvent_스키마를_사용한다() {
        val json = objectMapper.writeValueAsString(
            mapOf(
                "eventId" to "evt-006",
                "userId" to 100,
                "type" to "POINT_EARNED",
                "title" to "포인트 적립",
                "content" to "포인트가 적립되었습니다.",
                "metadata" to "extra-info"
            )
        )

        val record = converter.convertJsonToAvro("PointEarnedNotificationRequested", json)

        assertThat(record.get("eventId").toString()).isEqualTo("evt-006")
        assertThat(record.get("metadata").toString()).isEqualTo("extra-info")
    }

    @Test
    fun RefundCompletedEvent_JSON을_Avro_레코드로_변환한다() {
        val json = objectMapper.writeValueAsString(
            mapOf(
                "eventId" to "evt-007",
                "refundId" to 1,
                "orderId" to 10,
                "paymentId" to 1,
                "buyerId" to 100,
                "refundAmount" to "50000",
                "pointRefundAmount" to "500",
                "isFullRefund" to true,
                "couponId" to null,
                "items" to listOf(
                    mapOf(
                        "productId" to 1,
                        "quantity" to 2,
                        "refundPrice" to "25000"
                    )
                )
            )
        )

        val record = converter.convertJsonToAvro("RefundCompletedEvent", json)

        assertThat(record.get("refundAmount").toString()).isEqualTo("50000")
        assertThat(record.get("isFullRefund")).isEqualTo(true)
        assertThat(record.get("couponId")).isNull()
        @Suppress("UNCHECKED_CAST")
        val items = record.get("items") as List<*>
        assertThat(items).hasSize(1)
    }

    @Test
    fun PaymentCancellationRequestedEvent_JSON을_Avro_레코드로_변환한다() {
        val json = objectMapper.writeValueAsString(
            mapOf(
                "eventId" to "evt-008",
                "orderId" to 10
            )
        )

        val record = converter.convertJsonToAvro("PaymentCancellationRequested", json)

        assertThat(record.get("eventId").toString()).isEqualTo("evt-008")
        assertThat(record.get("orderId")).isEqualTo(10L)
    }

    @Test
    fun PaymentCancellationCompletedEvent_JSON을_Avro_레코드로_변환한다() {
        val json = objectMapper.writeValueAsString(
            mapOf(
                "eventId" to "evt-009",
                "orderId" to 10,
                "paymentId" to 1
            )
        )

        val record = converter.convertJsonToAvro("PaymentCancellationCompleted", json)

        assertThat(record.get("eventId").toString()).isEqualTo("evt-009")
        assertThat(record.get("paymentId")).isEqualTo(1L)
    }

    @Test
    fun PaymentCancellationFailedEvent_JSON을_Avro_레코드로_변환한다() {
        val json = objectMapper.writeValueAsString(
            mapOf(
                "eventId" to "evt-010",
                "orderId" to 10,
                "reason" to "이미 취소됨"
            )
        )

        val record = converter.convertJsonToAvro("PaymentCancellationFailed", json)

        assertThat(record.get("reason").toString()).isEqualTo("이미 취소됨")
    }

    @Test
    fun 알_수_없는_이벤트_타입이면_예외를_던진다() {
        val json = objectMapper.writeValueAsString(mapOf("field" to "value"))

        assertThatThrownBy {
            converter.convertJsonToAvro("UnknownEvent", json)
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("Unknown event type: UnknownEvent")
    }

    @Test
    fun PaymentReversalRequested_별칭으로_PaymentCancelledEvent_스키마를_사용한다() {
        val json = objectMapper.writeValueAsString(
            mapOf(
                "eventId" to "evt-011",
                "paymentId" to 5,
                "orderId" to 50,
                "userId" to 500,
                "amount" to "20000",
                "transactionId" to "tx-011"
            )
        )

        val record = converter.convertJsonToAvro("PaymentReversalRequested", json)

        assertThat(record.get("eventId").toString()).isEqualTo("evt-011")
        assertThat(record.get("paymentId")).isEqualTo(5L)
    }

    @Test
    fun LONG_타입_필드에_null이_오면_기본값_0을_사용한다() {
        val json = objectMapper.writeValueAsString(
            mapOf(
                "eventId" to "evt-012",
                "paymentId" to null,
                "orderId" to 10,
                "userId" to 100,
                "amount" to "30000",
                "reason" to ""
            )
        )

        val record = converter.convertJsonToAvro("PaymentFailedEvent", json)

        assertThat(record.get("paymentId")).isEqualTo(0L)
    }

    @Test
    fun RefundCompletedEvent_빈_items_배열을_처리한다() {
        val json = objectMapper.writeValueAsString(
            mapOf(
                "eventId" to "evt-013",
                "refundId" to 1,
                "orderId" to 10,
                "paymentId" to 1,
                "buyerId" to 100,
                "refundAmount" to "50000",
                "pointRefundAmount" to "0",
                "isFullRefund" to false,
                "couponId" to null,
                "items" to emptyList<Any>()
            )
        )

        val record = converter.convertJsonToAvro("RefundCompletedEvent", json)

        @Suppress("UNCHECKED_CAST")
        val items = record.get("items") as List<*>
        assertThat(items).isEmpty()
    }

    @Test
    fun BOOLEAN_타입_필드에_null이_오면_기본값_false를_사용한다() {
        val json = objectMapper.writeValueAsString(
            mapOf(
                "eventId" to "evt-014",
                "refundId" to 1,
                "orderId" to 10,
                "paymentId" to 1,
                "buyerId" to 100,
                "refundAmount" to "50000",
                "pointRefundAmount" to "0",
                "isFullRefund" to null,
                "couponId" to null,
                "items" to emptyList<Any>()
            )
        )

        val record = converter.convertJsonToAvro("RefundCompletedEvent", json)

        assertThat(record.get("isFullRefund")).isEqualTo(false)
    }

    @Test
    fun STRING_타입_필드에_null이_오면_빈_문자열을_사용한다() {
        val json = objectMapper.writeValueAsString(
            mapOf(
                "eventId" to "evt-015",
                "paymentId" to 1,
                "orderId" to 10,
                "userId" to 100,
                "amount" to null,
                "reason" to null
            )
        )

        val record = converter.convertJsonToAvro("PaymentFailedEvent", json)

        assertThat(record.get("amount").toString()).isEqualTo("")
        assertThat(record.get("reason").toString()).isEqualTo("")
    }
}
