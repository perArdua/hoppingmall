package com.hoppingmall.outbox.scheduler

import com.hoppingmall.outbox.domain.OutboxEvent
import com.hoppingmall.outbox.domain.OutboxStatus
import com.hoppingmall.outbox.repository.OutboxEventRepository
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
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.test.util.ReflectionTestUtils
import java.time.LocalDateTime

@DisplayName("OutboxMaintenanceScheduler")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class OutboxMaintenanceSchedulerTest {

    @Mock
    private lateinit var outboxEventRepository: OutboxEventRepository

    @Mock
    private lateinit var outboxEventPublisher: OutboxEventPublisher

    @InjectMocks
    private lateinit var outboxMaintenanceScheduler: OutboxMaintenanceScheduler

    private fun createStaleEvent(
        id: Long = 1L,
        retryCount: Int = 0
    ): OutboxEvent {
        val event = OutboxEvent(
            aggregateType = "Payment",
            aggregateId = "1",
            eventType = "PaymentCompleted",
            eventData = """{"paymentId":1}""",
            topic = "payment",
            partitionKey = "1",
            status = OutboxStatus.PENDING
        )
        ReflectionTestUtils.setField(event, "id", id)
        if (retryCount > 0) {
            ReflectionTestUtils.setField(event, "retryCount", retryCount)
        }
        return event
    }

    @Test
    fun 처리_완료된_이벤트를_정리한다() {
        whenever(outboxEventRepository.deleteProcessedEventsBefore(any<LocalDateTime>()))
            .thenReturn(5)

        outboxMaintenanceScheduler.cleanupProcessedEvents()

        verify(outboxEventRepository).deleteProcessedEventsBefore(any<LocalDateTime>())
    }

    @Test
    fun 삭제할_이벤트가_없으면_로그를_남기지_않는다() {
        whenever(outboxEventRepository.deleteProcessedEventsBefore(any<LocalDateTime>()))
            .thenReturn(0)

        outboxMaintenanceScheduler.cleanupProcessedEvents()

        verify(outboxEventRepository).deleteProcessedEventsBefore(any<LocalDateTime>())
    }

    @Test
    fun 지연된_이벤트를_재시도한다() {
        val staleEvent = createStaleEvent(id = 1L, retryCount = 0)

        whenever(outboxEventRepository.findStaleEvents(any<LocalDateTime>(), eq(50)))
            .thenReturn(listOf(staleEvent))
        whenever(
            outboxEventRepository.claimStaleEvent(
                id = eq(1L),
                nextStatus = eq(OutboxStatus.RETRYING),
                updatedAt = any(),
                cutoffDate = any(),
                maxRetries = eq(3),
                statuses = eq(listOf(OutboxStatus.PENDING, OutboxStatus.FAILED, OutboxStatus.RETRYING))
            )
        ).thenReturn(1)

        outboxMaintenanceScheduler.handleStaleEvents()

        verify(outboxEventPublisher).publishEvent(1L)
    }

    @Test
    fun 최대_재시도_초과된_지연_이벤트는_영구_실패_처리한다() {
        val staleEvent = createStaleEvent(id = 1L, retryCount = 3)

        whenever(outboxEventRepository.findStaleEvents(any<LocalDateTime>(), eq(50)))
            .thenReturn(listOf(staleEvent))
        whenever(outboxEventRepository.save(any<OutboxEvent>())).thenAnswer { it.arguments[0] as OutboxEvent }

        outboxMaintenanceScheduler.handleStaleEvents()

        assertThat(staleEvent.status).isEqualTo(OutboxStatus.FAILED)
        verify(outboxEventRepository).save(eq(staleEvent))
        verify(outboxEventPublisher, never()).publishEvent(any())
    }

    @Test
    fun 지연된_이벤트가_없으면_아무것도_하지_않는다() {
        whenever(outboxEventRepository.findStaleEvents(any<LocalDateTime>(), eq(50)))
            .thenReturn(emptyList())

        outboxMaintenanceScheduler.handleStaleEvents()

        verify(outboxEventPublisher, never()).publishEvent(any())
        verify(outboxEventRepository, never()).save(any<OutboxEvent>())
    }

    @Test
    fun 클레임_실패한_지연_이벤트는_발행하지_않는다() {
        val staleEvent = createStaleEvent(id = 1L, retryCount = 0)

        whenever(outboxEventRepository.findStaleEvents(any<LocalDateTime>(), eq(50)))
            .thenReturn(listOf(staleEvent))
        whenever(
            outboxEventRepository.claimStaleEvent(
                id = eq(1L),
                nextStatus = eq(OutboxStatus.RETRYING),
                updatedAt = any(),
                cutoffDate = any(),
                maxRetries = eq(3),
                statuses = eq(listOf(OutboxStatus.PENDING, OutboxStatus.FAILED, OutboxStatus.RETRYING))
            )
        ).thenReturn(0)

        outboxMaintenanceScheduler.handleStaleEvents()

        verify(outboxEventPublisher, never()).publishEvent(any())
    }
}
