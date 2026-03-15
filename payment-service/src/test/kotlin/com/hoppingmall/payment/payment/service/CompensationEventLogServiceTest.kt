package com.hoppingmall.payment.payment.service

import com.hoppingmall.payment.payment.domain.CompensationEventLog
import com.hoppingmall.payment.payment.domain.CompensationEventLogStatus
import com.hoppingmall.payment.payment.domain.repository.CompensationEventLogRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@DisplayName("CompensationEventLogService")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class CompensationEventLogServiceTest {

    @Mock
    private lateinit var compensationEventLogRepository: CompensationEventLogRepository

    @InjectMocks
    private lateinit var service: CompensationEventLogService

    @Test
    fun 새로운_이벤트_저장_시_PENDING_상태로_저장된다() {
        whenever(compensationEventLogRepository.findByEventId("evt-1")).thenReturn(null)
        val savedLog = CompensationEventLog(
            eventId = "evt-1",
            compensationType = "PAYMENT_FAILED",
            paymentId = 100L,
            orderId = 200L
        )
        whenever(compensationEventLogRepository.save(any<CompensationEventLog>())).thenReturn(savedLog)

        val result = service.saveIfAbsent("evt-1", "PAYMENT_FAILED", 100L, 200L)

        assertThat(result.eventId).isEqualTo("evt-1")
        assertThat(result.status).isEqualTo(CompensationEventLogStatus.PENDING)
        verify(compensationEventLogRepository).save(any<CompensationEventLog>())
    }

    @Test
    fun 이미_존재하는_이벤트는_기존_엔티티를_반환하고_저장하지_않는다() {
        val existingLog = CompensationEventLog(
            eventId = "evt-1",
            compensationType = "PAYMENT_FAILED",
            paymentId = 100L,
            orderId = 200L
        )
        whenever(compensationEventLogRepository.findByEventId("evt-1")).thenReturn(existingLog)

        val result = service.saveIfAbsent("evt-1", "PAYMENT_FAILED", 100L, 200L)

        assertThat(result).isSameAs(existingLog)
        verify(compensationEventLogRepository, never()).save(any<CompensationEventLog>())
    }

    @Test
    fun markCompleted_호출_시_PENDING에서_COMPLETED로_전환된다() {
        val log = CompensationEventLog(
            eventId = "evt-1",
            compensationType = "PAYMENT_FAILED",
            paymentId = 100L,
            orderId = 200L
        )
        assertThat(log.status).isEqualTo(CompensationEventLogStatus.PENDING)
        whenever(compensationEventLogRepository.findByEventId("evt-1")).thenReturn(log)

        service.markCompleted("evt-1")

        assertThat(log.status).isEqualTo(CompensationEventLogStatus.COMPLETED)
        assertThat(log.isCompleted()).isTrue()
    }

    @Test
    fun markCompleted_존재하지_않는_이벤트는_무시한다() {
        whenever(compensationEventLogRepository.findByEventId("evt-unknown")).thenReturn(null)

        service.markCompleted("evt-unknown")

        verify(compensationEventLogRepository).findByEventId("evt-unknown")
    }
}
