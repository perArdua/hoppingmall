package com.hoppingmall.outbox.scheduler

import com.hoppingmall.common.event.AvroEventConverter
import com.hoppingmall.outbox.metrics.OutboxMetrics
import com.hoppingmall.outbox.repository.OutboxEventRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.timeout
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.SimpleTransactionStatus
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@DisplayName("OutboxPublishWaker")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class OutboxPublishWakerTest {

    private val wakers = mutableListOf<OutboxPublishWaker>()

    @AfterEach
    fun tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization()
        }
        wakers.forEach { it.shutdown() }
    }

    private fun waker(publisher: OutboxEventPublisher, enabled: Boolean = true) =
        OutboxPublishWaker(publisher, enabled).also { wakers.add(it) }

    @Test
    fun 커밋_뒤_신호로_발행기가_바로_한_번_돈다() {
        val publisher = mock<OutboxEventPublisher>()
        val waker = waker(publisher)
        TransactionSynchronizationManager.initSynchronization()

        waker.requestAfterCommit()

        verify(publisher, never()).publishPendingEvents()
        TransactionSynchronizationManager.getSynchronizations().forEach { it.afterCommit() }
        verify(publisher, timeout(1_000).times(1)).publishPendingEvents()
    }

    @Test
    fun 롤백되면_신호를_보내지_않는다() {
        val publisher = mock<OutboxEventPublisher>()
        val waker = waker(publisher)
        TransactionSynchronizationManager.initSynchronization()

        waker.requestAfterCommit()
        TransactionSynchronizationManager.getSynchronizations()
            .forEach { it.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK) }

        Thread.sleep(300)
        verify(publisher, never()).publishPendingEvents()
    }

    @Test
    fun 플래그가_꺼지면_트랜잭션_동기화를_등록하지_않는다() {
        val publisher = mock<OutboxEventPublisher>()
        val waker = waker(publisher, enabled = false)
        TransactionSynchronizationManager.initSynchronization()

        waker.requestAfterCommit()
        waker.wake()

        assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty()
        Thread.sleep(200)
        verify(publisher, never()).publishPendingEvents()
    }

    @Test
    fun 연속_신호_10번과_예약_실행이_겹쳐도_배치는_한_번에_하나만_돌고_신호는_합쳐진다() {
        val repository = mock<OutboxEventRepository>()
        val transactionManager = mock<PlatformTransactionManager>()
        whenever(transactionManager.getTransaction(anyOrNull())).thenReturn(SimpleTransactionStatus())
        val running = AtomicInteger(0)
        val maxRunning = AtomicInteger(0)
        val batches = AtomicInteger(0)
        val firstBatchStarted = CountDownLatch(1)
        whenever(repository.lockUnprocessedEventIds(any(), any(), any(), any())).thenAnswer {
            val now = running.incrementAndGet()
            maxRunning.accumulateAndGet(now) { a, b -> maxOf(a, b) }
            batches.incrementAndGet()
            firstBatchStarted.countDown()
            Thread.sleep(100)
            running.decrementAndGet()
            emptyList<Long>()
        }
        val metrics = mock<OutboxMetrics>()
        val publisher = OutboxEventPublisher(
            repository, mock<KafkaTemplate<String, Any>>(), metrics, mock<AvroEventConverter>(), transactionManager,
            OutboxBrokerBackoff(metrics)
        )
        val waker = waker(publisher)

        waker.wake()
        assertThat(firstBatchStarted.await(1, TimeUnit.SECONDS)).isTrue()
        val scheduled = Thread { publisher.publishPendingEvents() }.apply { start() }
        repeat(10) { waker.wake() }
        scheduled.join(2_000)
        Thread.sleep(500)

        assertThat(maxRunning.get()).isEqualTo(1)
        assertThat(batches.get()).isEqualTo(3)
    }
}
