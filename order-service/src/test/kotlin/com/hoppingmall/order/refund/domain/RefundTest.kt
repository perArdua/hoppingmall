package com.hoppingmall.order.refund.domain

import com.hoppingmall.order.refund.enum.RefundReason
import com.hoppingmall.order.refund.enum.RefundStatus
import com.hoppingmall.order.refund.exception.RefundInvalidStatusException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import java.math.BigDecimal

@DisplayName("Refund")
@DisplayNameGeneration(ReplaceUnderscores::class)
class RefundTest {

    private fun createRefund(): Refund {
        return Refund.create(
            orderId = 1L,
            paymentId = 10L,
            buyerId = 100L,
            sellerId = 200L,
            reason = RefundReason.CHANGE_OF_MIND,
            reasonDetail = "단순 변심",
            refundAmount = BigDecimal("10000"),
            isFullRefund = false
        )
    }

    @Test
    fun 환불을_생성한다() {
        val refund = createRefund()

        assertThat(refund.orderId).isEqualTo(1L)
        assertThat(refund.paymentId).isEqualTo(10L)
        assertThat(refund.buyerId).isEqualTo(100L)
        assertThat(refund.sellerId).isEqualTo(200L)
        assertThat(refund.status).isEqualTo(RefundStatus.REQUESTED)
        assertThat(refund.reason).isEqualTo(RefundReason.CHANGE_OF_MIND)
        assertThat(refund.reasonDetail).isEqualTo("단순 변심")
        assertThat(refund.refundAmount).isEqualByComparingTo(BigDecimal("10000"))
        assertThat(refund.isFullRefund).isFalse()
    }

    @Test
    fun 환불을_승인한다() {
        val refund = createRefund()

        refund.approve(300L)

        assertThat(refund.status).isEqualTo(RefundStatus.APPROVED)
        assertThat(refund.approvedBy).isEqualTo(300L)
    }

    @Test
    fun 환불을_거절한다() {
        val refund = createRefund()

        refund.reject("사유 불충분", 300L)

        assertThat(refund.status).isEqualTo(RefundStatus.REJECTED)
        assertThat(refund.rejectionReason).isEqualTo("사유 불충분")
        assertThat(refund.approvedBy).isEqualTo(300L)
    }

    @Test
    fun 승인된_환불을_완료한다() {
        val refund = createRefund()
        refund.approve(300L)

        refund.complete()

        assertThat(refund.status).isEqualTo(RefundStatus.COMPLETED)
        assertThat(refund.completedAt).isNotNull()
    }

    @Test
    fun REQUESTED_에서_COMPLETED로_직접_전환하면_예외가_발생한다() {
        val refund = createRefund()

        assertThatThrownBy { refund.complete() }
            .isInstanceOf(RefundInvalidStatusException::class.java)
    }

    @Test
    fun COMPLETED_에서_APPROVED로_전환하면_예외가_발생한다() {
        val refund = createRefund()
        refund.approve(300L)
        refund.complete()

        assertThatThrownBy { refund.approve(300L) }
            .isInstanceOf(RefundInvalidStatusException::class.java)
    }

    @Test
    fun REJECTED_에서_APPROVED로_전환하면_예외가_발생한다() {
        val refund = createRefund()
        refund.reject("사유", 300L)

        assertThatThrownBy { refund.approve(300L) }
            .isInstanceOf(RefundInvalidStatusException::class.java)
    }

    @Test
    fun reasonDetail_없이_환불을_생성한다() {
        val refund = Refund.create(
            orderId = 1L, paymentId = 10L, buyerId = 100L, sellerId = 200L,
            reason = RefundReason.CHANGE_OF_MIND, reasonDetail = null,
            refundAmount = BigDecimal("5000"), isFullRefund = true
        )

        assertThat(refund.reasonDetail).isNull()
        assertThat(refund.isFullRefund).isTrue()
    }
}
