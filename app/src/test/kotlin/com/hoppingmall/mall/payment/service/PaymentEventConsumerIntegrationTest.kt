package com.hoppingmall.mall.payment.service

import com.hoppingmall.mall.payment.domain.repository.PaymentEventLogRepository
import com.hoppingmall.mall.payment.dto.event.PaymentCompletedEvent
import com.hoppingmall.mall.payment.enum.PaymentMethod
import com.hoppingmall.mall.payment.enum.PaymentStatus
import com.hoppingmall.mall.support.IntegrationTestBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.kafka.core.KafkaTemplate
import java.math.BigDecimal
import java.time.LocalDateTime

@DisplayName("PaymentEventConsumer 통합 테스트")
@DisplayNameGeneration(ReplaceUnderscores::class)
class PaymentEventConsumerIntegrationTest : IntegrationTestBase() {

    @Autowired
    private lateinit var kafkaTemplate: KafkaTemplate<String, Any>

    @Autowired
    private lateinit var paymentEventLogRepository: PaymentEventLogRepository

    @Test
    fun 결제_완료_이벤트를_수신하여_처리한다() {
        val event = PaymentCompletedEvent(
            paymentId = 1L,
            orderId = 100L,
            userId = 1L,
            amount = BigDecimal("50000"),
            pointAmount = BigDecimal("500"),
            method = PaymentMethod.CREDIT_CARD,
            status = PaymentStatus.SUCCESS,
            transactionId = "tx-integration-001",
            completedAt = LocalDateTime.now()
        )

        kafkaTemplate.send("payment", event.orderId.toString(), event)

        awaitUntil {
            paymentEventLogRepository.existsByTransactionId("tx-integration-001")
        }

        val logs = paymentEventLogRepository.findAll()
        val saved = logs.find { it.transactionId == "tx-integration-001" }
        assertThat(saved).isNotNull
        assertThat(saved!!.paymentId).isEqualTo(1L)
        assertThat(saved.orderId).isEqualTo(100L)
    }

    @Test
    fun 중복_결제_이벤트는_무시된다() {
        val event = PaymentCompletedEvent(
            paymentId = 2L,
            orderId = 200L,
            userId = 2L,
            amount = BigDecimal("30000"),
            pointAmount = BigDecimal("300"),
            method = PaymentMethod.BANK_TRANSFER,
            status = PaymentStatus.SUCCESS,
            transactionId = "tx-dedup-001",
            completedAt = LocalDateTime.now()
        )

        kafkaTemplate.send("payment", event.orderId.toString(), event)

        awaitUntil {
            paymentEventLogRepository.existsByTransactionId("tx-dedup-001")
        }

        kafkaTemplate.send("payment", event.orderId.toString(), event)
        Thread.sleep(1000)

        val count = paymentEventLogRepository.findAll()
            .count { it.transactionId == "tx-dedup-001" }
        assertThat(count).isEqualTo(1)
    }
}
