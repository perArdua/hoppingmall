package com.hoppingmall.payment.payment.service

import com.hoppingmall.payment.payment.domain.CompensationEventLog
import com.hoppingmall.payment.payment.domain.CompensationEventLogStatus
import com.hoppingmall.payment.payment.domain.repository.CompensationEventLogRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Service
class CompensationEventLogService(
    private val compensationEventLogRepository: CompensationEventLogRepository
) {

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun saveIfAbsent(
        eventId: String,
        compensationType: String,
        paymentId: Long,
        orderId: Long
    ): CompensationEventLog {
        val existing = compensationEventLogRepository.findByEventId(eventId)
        if (existing != null) {
            return existing
        }

        return try {
            compensationEventLogRepository.save(
                CompensationEventLog(
                    eventId = eventId,
                    compensationType = compensationType,
                    paymentId = paymentId,
                    orderId = orderId,
                    status = CompensationEventLogStatus.PENDING
                )
            )
        } catch (e: DataIntegrityViolationException) {
            compensationEventLogRepository.findByEventId(eventId)!!
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun markCompleted(eventId: String) {
        val log = compensationEventLogRepository.findByEventId(eventId)
            ?: return
        log.complete()
        compensationEventLogRepository.save(log)
    }
}
