package com.hoppingmall.payment.payment.service

import com.hoppingmall.common.BaseEntity
import com.hoppingmall.payment.payment.domain.Payment
import com.hoppingmall.payment.payment.domain.repository.PaymentRepository
import com.hoppingmall.payment.payment.enum.PaymentMethod
import com.hoppingmall.payment.payment.enum.PaymentStatus
import com.hoppingmall.payment.payment.exception.PaymentAccessDeniedException
import com.hoppingmall.payment.payment.exception.PaymentNotFoundException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.SliceImpl
import java.math.BigDecimal
import java.util.Optional

@DisplayName("PaymentQueryServiceImpl")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class PaymentQueryServiceImplTest {

    @Mock
    private lateinit var paymentRepository: PaymentRepository

    @InjectMocks
    private lateinit var service: PaymentQueryServiceImpl

    private fun createPayment(id: Long = 1L, userId: Long = 10L): Payment {
        val payment = Payment.create(
            orderId = 100L,
            userId = userId,
            amount = BigDecimal("50000"),
            method = PaymentMethod.CREDIT_CARD
        )
        val idField = BaseEntity::class.java.getDeclaredField("id")
        idField.isAccessible = true
        idField.set(payment, id)
        return payment
    }

    @Test
    fun getPaymentById_결제를_찾고_userId가_일치하면_PaymentResponse를_반환한다() {
        val payment = createPayment(id = 1L, userId = 10L)
        whenever(paymentRepository.findById(1L)).thenReturn(Optional.of(payment))

        val result = service.getPaymentById(1L, 10L)

        assertThat(result.id).isEqualTo(1L)
        assertThat(result.userId).isEqualTo(10L)
        assertThat(result.orderId).isEqualTo(100L)
    }

    @Test
    fun getPaymentById_결제가_없으면_PaymentNotFoundException을_던진다() {
        whenever(paymentRepository.findById(99L)).thenReturn(Optional.empty())

        assertThatThrownBy { service.getPaymentById(99L, 10L) }
            .isInstanceOf(PaymentNotFoundException::class.java)
    }

    @Test
    fun getPaymentById_userId가_일치하지_않으면_PaymentAccessDeniedException을_던진다() {
        val payment = createPayment(id = 1L, userId = 10L)
        whenever(paymentRepository.findById(1L)).thenReturn(Optional.of(payment))

        assertThatThrownBy { service.getPaymentById(1L, 999L) }
            .isInstanceOf(PaymentAccessDeniedException::class.java)
    }

    @Test
    fun getPaymentsByUserId_PaymentResponse의_Slice를_반환한다() {
        val payment = createPayment(id = 1L, userId = 10L)
        val pageable = PageRequest.of(0, 10)
        val slice = SliceImpl(listOf(payment), pageable, false)
        whenever(paymentRepository.findByUserId(10L, pageable)).thenReturn(slice)

        val result = service.getPaymentsByUserId(10L, pageable)

        assertThat(result.content).hasSize(1)
        assertThat(result.content[0].userId).isEqualTo(10L)
    }

    @Test
    fun getPaymentsByOrderId_결제가_있고_userId가_일치하면_리스트를_반환한다() {
        val payment = createPayment(id = 1L, userId = 10L)
        whenever(paymentRepository.findByOrderId(100L)).thenReturn(payment)

        val result = service.getPaymentsByOrderId(100L, 10L)

        assertThat(result).hasSize(1)
        assertThat(result[0].orderId).isEqualTo(100L)
    }

    @Test
    fun getPaymentsByOrderId_결제가_없으면_빈_리스트를_반환한다() {
        whenever(paymentRepository.findByOrderId(999L)).thenReturn(null)

        val result = service.getPaymentsByOrderId(999L, 10L)

        assertThat(result).isEmpty()
    }

    @Test
    fun getPaymentsByOrderId_userId가_일치하지_않으면_PaymentAccessDeniedException을_던진다() {
        val payment = createPayment(id = 1L, userId = 10L)
        whenever(paymentRepository.findByOrderId(100L)).thenReturn(payment)

        assertThatThrownBy { service.getPaymentsByOrderId(100L, 999L) }
            .isInstanceOf(PaymentAccessDeniedException::class.java)
    }

    @Test
    fun getPaymentByTransactionId_결제가_있으면_PaymentResponse를_반환한다() {
        val payment = createPayment(id = 1L, userId = 10L)
        payment.updateStatus(PaymentStatus.SUCCESS, transactionId = "txn-abc")
        whenever(paymentRepository.findByTransactionId("txn-abc")).thenReturn(payment)

        val result = service.getPaymentByTransactionId("txn-abc")

        assertThat(result).isNotNull
        assertThat(result!!.transactionId).isEqualTo("txn-abc")
    }

    @Test
    fun getPaymentByTransactionId_결제가_없으면_null을_반환한다() {
        whenever(paymentRepository.findByTransactionId("txn-none")).thenReturn(null)

        val result = service.getPaymentByTransactionId("txn-none")

        assertThat(result).isNull()
    }
}
