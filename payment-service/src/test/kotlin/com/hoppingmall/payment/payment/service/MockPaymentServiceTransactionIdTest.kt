package com.hoppingmall.payment.payment.service

import com.hoppingmall.payment.payment.domain.Payment
import com.hoppingmall.payment.payment.enum.PaymentMethod
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@DisplayName("모의 결제 거래 ID 생성")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class MockPaymentServiceTransactionIdTest {

    private val mockPaymentService = MockPaymentService()

    @Test
    fun 같은_순간에_여러_스레드가_1만_개를_만들어도_거래_ID가_겹치지_않는다() {
        val threads = 16
        val perThread = 625
        val payment = Payment.create(
            orderId = 1L,
            userId = 1L,
            amount = BigDecimal("1000.00"),
            method = PaymentMethod.CREDIT_CARD
        )
        val ids = ConcurrentHashMap.newKeySet<String>()
        val generated = AtomicInteger()
        val ready = CountDownLatch(threads)
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(threads)

        try {
            repeat(threads) {
                executor.submit {
                    ready.countDown()
                    start.await()
                    repeat(perThread) {
                        val result = mockPaymentService.processPayment(payment)
                        ids.add(result.transactionId!!)
                        generated.incrementAndGet()
                    }
                }
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue()
            start.countDown()
        } finally {
            executor.shutdown()
            assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue()
        }

        assertThat(generated.get()).isEqualTo(threads * perThread)
        assertThat(ids).hasSize(threads * perThread)
    }

    @Test
    fun 거래_ID는_MOCK_접두사를_유지하고_가장_좁은_파생_이벤트_ID_컬럼에도_들어간다() {
        val payment = Payment.create(
            orderId = 1L,
            userId = 1L,
            amount = BigDecimal("1000.00"),
            method = PaymentMethod.CREDIT_CARD
        )

        val transactionId = mockPaymentService.processPayment(payment).transactionId!!

        assertThat(transactionId).startsWith("MOCK_")
        assertThat("payment-completed-$transactionId".length).isLessThanOrEqualTo(100)
    }
}
