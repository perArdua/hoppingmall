package com.hoppingmall.outbox.service

import com.hoppingmall.outbox.domain.OutboxEvent
import com.hoppingmall.outbox.domain.OutboxStatus
import com.hoppingmall.outbox.repository.OutboxEventRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class OutboxEventService(
    private val outboxEventRepository: OutboxEventRepository,
    private val outboxEventWriter: OutboxEventWriter
) {

    private val logger = LoggerFactory.getLogger(OutboxEventService::class.java)

    @Transactional
    fun saveEvent(
        aggregateType: String,
        aggregateId: String,
        eventType: String,
        eventData: Any,
        topic: String,
        partitionKey: String? = null
    ) {
        outboxEventWriter.saveEvent(
            aggregateType = aggregateType,
            aggregateId = aggregateId,
            eventType = eventType,
            eventData = eventData,
            topic = topic,
            partitionKey = partitionKey
        )
    }

    fun getEventsByAggregateId(aggregateId: String, aggregateType: String): List<OutboxEvent> =
        outboxEventRepository.findByAggregateIdAndType(aggregateId, aggregateType)

    fun getEventStats(): Map<String, Long> {
        return mapOf(
            "pending" to outboxEventRepository.countByStatus(OutboxStatus.PENDING),
            "retrying" to outboxEventRepository.countByStatus(OutboxStatus.RETRYING),
            "published" to outboxEventRepository.countByStatus(OutboxStatus.PUBLISHED),
            "failed" to outboxEventRepository.countByStatus(OutboxStatus.FAILED)
        )
    }

    /**
     * 멈춘(parked) 행은 메시지 쪽 실패로 재시도 3회를 다 써서 status = 'FAILED', processed = true가 되어 발행에서 빠진 행이다.
     * 원인을 고친 뒤 이 메서드나 다음 SQL로 다시 넣으면 다음 발행 주기에 나간다.
     * UPDATE outbox_events SET status = 'PENDING', retry_count = 0, processed = false, processed_at = NULL, updated_at = NOW(6) WHERE id IN (...) AND processed = true AND status = 'FAILED';
     * 멈춘 행은 처리 완료 정리 작업에서 빠지므로 다시 넣을 때까지 남아 있고 outbox.parked 지표로 셀 수 있다.
     */
    @Transactional
    fun requeueParked(ids: List<Long>): Int {
        if (ids.isEmpty()) return 0
        val requeued = outboxEventRepository.requeueParkedEvents(
            ids = ids,
            pendingStatus = OutboxStatus.PENDING,
            failedStatus = OutboxStatus.FAILED,
            updatedAt = LocalDateTime.now()
        )
        logger.info("Outbox requeued $requeued of ${ids.size} parked events for publishing: ids=$ids")
        return requeued
    }
}
