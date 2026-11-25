package com.hoppingmall.outbox.scheduler

import com.hoppingmall.outbox.domain.OutboxEvent
import com.hoppingmall.outbox.domain.OutboxStatus
import com.hoppingmall.outbox.repository.OUTBOX_CLEANUP_BATCH_SIZE
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
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.transaction.PlatformTransactionManager
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

    @Mock
    private lateinit var transactionManager: PlatformTransactionManager

    @InjectMocks
    private lateinit var outboxMaintenanceScheduler: OutboxMaintenanceScheduler

    private fun createStaleEvent(
        id: Long = 1L,
        retryCount: Int = 0,
        status: OutboxStatus = OutboxStatus.PENDING
    ): OutboxEvent {
        val event = OutboxEvent(
            aggregateType = "Payment",
            aggregateId = "1",
            eventType = "PaymentCompleted",
            eventData = """{"paymentId":1}""",
            topic = "payment",
            partitionKey = "1",
            status = status
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
    fun 배치가_가득_차면_같은_실행에서_이어서_지운다() {
        whenever(outboxEventRepository.deleteProcessedEventsBefore(any<LocalDateTime>()))
            .thenReturn(OUTBOX_CLEANUP_BATCH_SIZE, OUTBOX_CLEANUP_BATCH_SIZE, 3)

        outboxMaintenanceScheduler.cleanupProcessedEvents()

        verify(outboxEventRepository, times(3)).deleteProcessedEventsBefore(any<LocalDateTime>())
    }

    @Test
    fun 실행당_배치_상한에서_멈춘다() {
        ReflectionTestUtils.setField(outboxMaintenanceScheduler, "cleanupMaxBatches", 2)
        whenever(outboxEventRepository.deleteProcessedEventsBefore(any<LocalDateTime>()))
            .thenReturn(OUTBOX_CLEANUP_BATCH_SIZE)

        outboxMaintenanceScheduler.cleanupProcessedEvents()

        verify(outboxEventRepository, times(2)).deleteProcessedEventsBefore(any<LocalDateTime>())
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
    fun 일괄_선점이_켜져_있으면_발행기가_준_선점_시각으로_선점하고_그_시각을_넘겨_발행한다() {
        val staleEvent = createStaleEvent(id = 1L, retryCount = 0)
        val claimedAt = LocalDateTime.of(2026, 11, 20, 0, 0, 0, 123_456_000)

        whenever(outboxEventRepository.findStaleEvents(any<LocalDateTime>(), eq(50)))
            .thenReturn(listOf(staleEvent))
        whenever(outboxEventPublisher.staleClaimTimestamp()).thenReturn(claimedAt)
        whenever(
            outboxEventRepository.claimStaleEvent(
                id = eq(1L),
                nextStatus = eq(OutboxStatus.RETRYING),
                updatedAt = eq(claimedAt),
                cutoffDate = any(),
                maxRetries = eq(3),
                statuses = eq(listOf(OutboxStatus.PENDING, OutboxStatus.FAILED, OutboxStatus.RETRYING))
            )
        ).thenReturn(1)

        outboxMaintenanceScheduler.handleStaleEvents()

        verify(outboxEventPublisher).publishEvent(1L, claimedAt)
    }

    @Test
    fun 일괄_선점이_꺼져_있으면_현재_시각으로_선점하고_선점_시각_없이_발행한다() {
        val staleEvent = createStaleEvent(id = 1L, retryCount = 0)
        val before = LocalDateTime.now()

        whenever(outboxEventRepository.findStaleEvents(any<LocalDateTime>(), eq(50)))
            .thenReturn(listOf(staleEvent))
        whenever(outboxEventPublisher.staleClaimTimestamp()).thenReturn(null)
        val claimTimes = mutableListOf<LocalDateTime>()
        whenever(outboxEventRepository.claimStaleEvent(any(), any(), any(), any(), any(), any())).thenAnswer {
            claimTimes.add(it.arguments[2] as LocalDateTime)
            1
        }

        outboxMaintenanceScheduler.handleStaleEvents()

        assertThat(claimTimes).hasSize(1)
        assertThat(claimTimes[0]).isBetween(before, LocalDateTime.now())
        verify(outboxEventPublisher).publishEvent(1L, null)
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
        verify(outboxEventPublisher, never()).publishEvent(any(), anyOrNull())
    }

    @Test
    fun 지연된_이벤트가_없으면_아무것도_하지_않는다() {
        whenever(outboxEventRepository.findStaleEvents(any<LocalDateTime>(), eq(50)))
            .thenReturn(emptyList())

        outboxMaintenanceScheduler.handleStaleEvents()

        verify(outboxEventPublisher, never()).publishEvent(any(), anyOrNull())
        verify(outboxEventRepository, never()).save(any<OutboxEvent>())
    }

    @Test
    fun null_id를_가진_지연_이벤트는_건너뛴다() {
        val eventWithNullId = OutboxEvent(
            aggregateType = "Payment",
            aggregateId = "1",
            eventType = "PaymentCompleted",
            eventData = """{"paymentId":1}""",
            topic = "payment",
            partitionKey = "1",
            status = OutboxStatus.PENDING
        )

        whenever(outboxEventRepository.findStaleEvents(any<LocalDateTime>(), eq(50)))
            .thenReturn(listOf(eventWithNullId))

        outboxMaintenanceScheduler.handleStaleEvents()

        verify(outboxEventPublisher, never()).publishEvent(any(), anyOrNull())
        verify(outboxEventRepository, never()).claimStaleEvent(any(), any(), any(), any(), any(), any())
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

        verify(outboxEventPublisher, never()).publishEvent(any(), anyOrNull())
    }

    @Test
    fun 브로커_백오프_중에는_지연된_이벤트를_선점하지_않는다() {
        val staleEvent = createStaleEvent(id = 1L, retryCount = 0)

        whenever(outboxEventRepository.findStaleEvents(any<LocalDateTime>(), eq(50)))
            .thenReturn(listOf(staleEvent))
        whenever(outboxEventPublisher.isBrokerBackingOff()).thenReturn(true)

        outboxMaintenanceScheduler.handleStaleEvents()

        verify(outboxEventRepository, never()).claimStaleEvent(any(), any(), any(), any(), any(), any())
        verify(outboxEventPublisher, never()).publishEvent(any(), anyOrNull())
        verify(outboxEventRepository, never()).save(any<OutboxEvent>())
    }

    @Test
    fun 복구_중_브로커_백오프가_걸리면_남은_지연_이벤트는_다음_실행으로_미룬다() {
        val first = createStaleEvent(id = 1L)
        val second = createStaleEvent(id = 2L)

        whenever(outboxEventRepository.findStaleEvents(any<LocalDateTime>(), eq(50)))
            .thenReturn(listOf(first, second))
        whenever(outboxEventPublisher.isBrokerBackingOff()).thenReturn(false, true)
        whenever(outboxEventRepository.claimStaleEvent(any(), any(), any(), any(), any(), any())).thenReturn(1)

        outboxMaintenanceScheduler.handleStaleEvents()

        verify(outboxEventPublisher).publishEvent(1L, null)
        verify(outboxEventPublisher, never()).publishEvent(eq(2L), anyOrNull())
    }

    @Test
    fun 브로커_실패로_PENDING에_돌아간_행은_재시도_소진으로_보지_않고_다시_발행한다() {
        val released = createStaleEvent(id = 1L, retryCount = 2, status = OutboxStatus.PENDING)

        whenever(outboxEventRepository.findStaleEvents(any<LocalDateTime>(), eq(50)))
            .thenReturn(listOf(released))
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

        assertThat(released.processed).isFalse()
        assertThat(released.retryCount).isEqualTo(2)
        verify(outboxEventRepository, never()).save(any<OutboxEvent>())
        verify(outboxEventPublisher).publishEvent(1L, null)
    }
}
