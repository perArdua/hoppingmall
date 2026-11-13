package com.hoppingmall.common.event

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test

@DisplayName("AvroEventConverter 빠진 필드 처리")
@DisplayNameGeneration(ReplaceUnderscores::class)
class AvroEventConverterMissingFieldTest {

    private val objectMapper = ObjectMapper()
    private val converter = AvroEventConverter(objectMapper)

    private fun convert(eventType: String, data: Map<String, Any?>) =
        converter.convertJsonToAvro(eventType, objectMapper.writeValueAsString(data))

    private val paymentCancelled = mapOf(
        "eventId" to "payment-cancelled-1",
        "paymentId" to 1L,
        "orderId" to 10L,
        "userId" to 100L,
        "amount" to "10000",
        "transactionId" to "tx-1"
    )

    private val paymentCompleted = mapOf(
        "paymentId" to 1L,
        "orderId" to 10L,
        "userId" to 100L,
        "amount" to "10000",
        "pointAmount" to "0",
        "method" to "CREDIT_CARD",
        "status" to "SUCCESS",
        "transactionId" to "tx-1",
        "completedAt" to "2026-11-14T10:00:00"
    )

    @Test
    fun 기본값이_없는_LONG_필드가_빠지면_0으로_채우지_않고_변환에_실패한다() {
        assertThatThrownBy { convert("PaymentCancelled", paymentCancelled - "paymentId") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("paymentId")
    }

    @Test
    fun 기본값이_없는_STRING_필드가_빠지면_빈_문자열로_채우지_않고_변환에_실패한다() {
        assertThatThrownBy { convert("PaymentCancelled", paymentCancelled - "amount") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("amount")
    }

    @Test
    fun 기본값이_없는_필드에_null이_오면_빠진_것과_같이_변환에_실패한다() {
        assertThatThrownBy { convert("PaymentCompleted", paymentCompleted + ("transactionId" to null)) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("transactionId")
    }

    @Test
    fun 기본값이_없는_ENUM_필드가_빠지면_변환에_실패한다() {
        assertThatThrownBy {
            convert(
                "PaymentCompletedNotificationRequested",
                mapOf("eventId" to "n-1", "userId" to 100L, "title" to "제목", "content" to "내용")
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("type")
    }

    @Test
    fun 중첩_레코드의_기본값이_없는_필드가_빠지면_변환에_실패한다() {
        assertThatThrownBy {
            convert(
                "RefundCompleted",
                mapOf(
                    "eventId" to "refund-1",
                    "refundId" to 1L,
                    "orderId" to 10L,
                    "paymentId" to 1L,
                    "buyerId" to 100L,
                    "refundAmount" to "10000",
                    "pointRefundAmount" to "0",
                    "isFullRefund" to true,
                    "couponId" to null,
                    "items" to listOf(mapOf("quantity" to 1, "refundPrice" to "10000"))
                )
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("productId")
    }

    @Test
    fun 기본값이_있는_필드가_빠지면_스키마의_기본값을_쓴다() {
        val completed = convert("PaymentCompleted", paymentCompleted - "pointAmount")
        val earn = convert(
            "PointEarnRequested",
            mapOf("eventId" to "e-1", "userId" to 100L, "orderId" to 10L, "paymentId" to 1L, "earnAmount" to "100")
        )

        assertThat(completed.get("pointAmount").toString()).isEqualTo("0")
        assertThat(earn.get("reason").toString()).isEqualTo("결제 완료")
    }

    @Test
    fun 널을_허용하는_필드가_빠지면_null로_둔다() {
        val record = convert(
            "MembershipRevertRequested",
            mapOf(
                "eventId" to "membership-revert-cancel-1",
                "userId" to 100L,
                "orderId" to 10L,
                "paymentId" to 1L,
                "amount" to "10000",
                "reason" to "CANCEL"
            )
        )

        assertThat(record.get("refundId")).isNull()
    }

    @Test
    fun 역보상_요청에_결제_ID가_빠지면_0으로_채우지_않고_변환에_실패한다() {
        assertThatThrownBy {
            convert(
                "PaymentReversalRequested",
                mapOf("eventId" to "timeout-1", "orderId" to 10L, "userId" to 100L, "amount" to "10000", "reason" to "SAGA_TIMEOUT")
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("Missing required field: PaymentReversalRequested.paymentId")
    }

    @Test
    fun 역보상_요청에_금액이_빠지면_빈_문자열로_채우지_않고_변환에_실패한다() {
        assertThatThrownBy {
            convert(
                "PaymentReversalRequested",
                mapOf("eventId" to "reversal-tx-1", "orderId" to 10L, "paymentId" to 1L, "userId" to 100L, "reason" to "INVENTORY_CONFIRM_FAILED")
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("Missing required field: PaymentReversalRequested.amount")
    }

    @Test
    fun 결제를_찾지_못한_역보상의_결제_ID_0과_금액_0은_값이_있으므로_그대로_변환한다() {
        val record = convert(
            "PaymentReversalRequested",
            mapOf("eventId" to "timeout-2", "orderId" to 10L, "paymentId" to 0L, "userId" to 100L, "amount" to "0", "reason" to "SAGA_TIMEOUT")
        )

        assertThat(record.get("paymentId")).isEqualTo(0L)
        assertThat(record.get("amount").toString()).isEqualTo("0")
        assertThat(record.get("orderId")).isEqualTo(10L)
    }
}
