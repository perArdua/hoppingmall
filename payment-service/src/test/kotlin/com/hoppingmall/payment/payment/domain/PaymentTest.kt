package com.hoppingmall.payment.payment.domain

import com.hoppingmall.common.BaseEntity
import com.hoppingmall.payment.payment.enum.PaymentMethod
import com.hoppingmall.payment.payment.enum.PaymentStatus
import com.hoppingmall.payment.payment.exception.PaymentInvalidStateException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDateTime

@DisplayName("Payment 도메인")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class PaymentTest {

    private fun createPayment(
        orderId: Long = 1L,
        userId: Long = 10L,
        amount: BigDecimal = BigDecimal("50000"),
        method: PaymentMethod = PaymentMethod.CREDIT_CARD,
        pointAmount: BigDecimal = BigDecimal.ZERO,
        couponId: Long? = null,
        couponDiscountAmount: BigDecimal = BigDecimal.ZERO
    ): Payment {
        val payment = Payment.create(
            orderId = orderId,
            userId = userId,
            amount = amount,
            method = method,
            pointAmount = pointAmount,
            couponId = couponId,
            couponDiscountAmount = couponDiscountAmount
        )
        val idField = BaseEntity::class.java.getDeclaredField("id")
        idField.isAccessible = true
        idField.set(payment, 1L)
        return payment
    }

    @Test
    fun create_올바른_필드로_Payment를_생성한다() {
        val payment = Payment.create(
            orderId = 100L,
            userId = 20L,
            amount = BigDecimal("30000"),
            method = PaymentMethod.BANK_TRANSFER,
            pointAmount = BigDecimal("1000"),
            couponId = 5L,
            couponDiscountAmount = BigDecimal("2000")
        )

        assertThat(payment.orderId).isEqualTo(100L)
        assertThat(payment.userId).isEqualTo(20L)
        assertThat(payment.amount).isEqualByComparingTo(BigDecimal("30000"))
        assertThat(payment.method).isEqualTo(PaymentMethod.BANK_TRANSFER)
        assertThat(payment.pointAmount).isEqualByComparingTo(BigDecimal("1000"))
        assertThat(payment.couponId).isEqualTo(5L)
        assertThat(payment.couponDiscountAmount).isEqualByComparingTo(BigDecimal("2000"))
        assertThat(payment.status).isEqualTo(PaymentStatus.PENDING)
        assertThat(payment.transactionId).isNull()
        assertThat(payment.errorMessage).isNull()
        assertThat(payment.completedAt).isNull()
    }

    @Test
    fun updateStatus_상태와_트랜잭션ID와_완료시각과_에러메시지를_업데이트한다() {
        val payment = createPayment()
        val completedAt = LocalDateTime.of(2024, 1, 1, 12, 0)

        payment.updateStatus(
            newStatus = PaymentStatus.SUCCESS,
            transactionId = "txn-123",
            completedAt = completedAt,
            errorMessage = null
        )

        assertThat(payment.status).isEqualTo(PaymentStatus.SUCCESS)
        assertThat(payment.transactionId).isEqualTo("txn-123")
        assertThat(payment.completedAt).isEqualTo(completedAt)
        assertThat(payment.errorMessage).isNull()
    }

    @Test
    fun updateStatus_실패_상태로_에러메시지를_설정한다() {
        val payment = createPayment()

        payment.updateStatus(
            newStatus = PaymentStatus.FAILED,
            transactionId = null,
            completedAt = null,
            errorMessage = "카드 한도 초과"
        )

        assertThat(payment.status).isEqualTo(PaymentStatus.FAILED)
        assertThat(payment.transactionId).isNull()
        assertThat(payment.completedAt).isNull()
        assertThat(payment.errorMessage).isEqualTo("카드 한도 초과")
    }

    @Test
    fun isSuccess_SUCCESS_상태이면_true를_반환한다() {
        val payment = createPayment()
        payment.updateStatus(PaymentStatus.SUCCESS)

        assertThat(payment.isSuccess()).isTrue()
    }

    @Test
    fun isSuccess_PENDING_상태이면_false를_반환한다() {
        val payment = createPayment()

        assertThat(payment.isSuccess()).isFalse()
    }

    @Test
    fun isSuccess_FAILED_상태이면_false를_반환한다() {
        val payment = createPayment()
        payment.updateStatus(PaymentStatus.FAILED)

        assertThat(payment.isSuccess()).isFalse()
    }

    @Test
    fun isFailed_FAILED_상태이면_true를_반환한다() {
        val payment = createPayment()
        payment.updateStatus(PaymentStatus.FAILED)

        assertThat(payment.isFailed()).isTrue()
    }

    @Test
    fun isFailed_SUCCESS_상태이면_false를_반환한다() {
        val payment = createPayment()
        payment.updateStatus(PaymentStatus.SUCCESS)

        assertThat(payment.isFailed()).isFalse()
    }

    @Test
    fun isFailed_PENDING_상태이면_false를_반환한다() {
        val payment = createPayment()

        assertThat(payment.isFailed()).isFalse()
    }

    @Test
    fun FAILED_상태에서_상태_변경_시_예외가_발생한다() {
        val payment = createPayment()
        payment.updateStatus(PaymentStatus.FAILED)

        assertThatThrownBy { payment.updateStatus(PaymentStatus.SUCCESS) }
            .isInstanceOf(PaymentInvalidStateException::class.java)
    }

    @Test
    fun CANCELLED_상태에서_상태_변경_시_예외가_발생한다() {
        val payment = createPayment()
        payment.updateStatus(PaymentStatus.CANCELLED)

        assertThatThrownBy { payment.updateStatus(PaymentStatus.SUCCESS) }
            .isInstanceOf(PaymentInvalidStateException::class.java)
    }

    @Test
    fun REFUNDED_상태에서_상태_변경_시_예외가_발생한다() {
        val payment = createPayment()
        payment.updateStatus(PaymentStatus.SUCCESS)
        payment.updateStatus(PaymentStatus.REFUNDED)

        assertThatThrownBy { payment.updateStatus(PaymentStatus.SUCCESS) }
            .isInstanceOf(PaymentInvalidStateException::class.java)
    }

    @Test
    fun SUCCESS에서_PENDING으로_전환_시_예외가_발생한다() {
        val payment = createPayment()
        payment.updateStatus(PaymentStatus.SUCCESS)

        assertThatThrownBy { payment.updateStatus(PaymentStatus.PENDING) }
            .isInstanceOf(PaymentInvalidStateException::class.java)
    }

    @Test
    fun copy_동일한_값을_가진_새로운_Payment를_생성한다() {
        val original = createPayment(
            orderId = 100L,
            userId = 20L,
            amount = BigDecimal("50000"),
            method = PaymentMethod.CREDIT_CARD,
            pointAmount = BigDecimal("1000"),
            couponId = 3L,
            couponDiscountAmount = BigDecimal("500")
        )
        original.updateStatus(PaymentStatus.SUCCESS, transactionId = "txn-copy")

        val copied = original.copy()

        assertThat(copied.orderId).isEqualTo(original.orderId)
        assertThat(copied.userId).isEqualTo(original.userId)
        assertThat(copied.amount).isEqualByComparingTo(original.amount)
        assertThat(copied.method).isEqualTo(original.method)
        assertThat(copied.pointAmount).isEqualByComparingTo(original.pointAmount)
        assertThat(copied.couponId).isEqualTo(original.couponId)
        assertThat(copied.couponDiscountAmount).isEqualByComparingTo(original.couponDiscountAmount)
        assertThat(copied.status).isEqualTo(original.status)
        assertThat(copied.transactionId).isEqualTo(original.transactionId)
        assertThat(copied).isNotSameAs(original)
    }
}
