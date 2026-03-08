package com.hoppingmall.mall.membership.service

import com.hoppingmall.mall.membership.domain.MembershipEventLog
import com.hoppingmall.mall.membership.domain.repository.MembershipEventLogRepository
import com.hoppingmall.mall.payment.dto.event.MembershipUpdateRequestEvent
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class MembershipEventConsumer(
    private val membershipCommandService: MembershipCommandService,
    private val membershipEventLogRepository: MembershipEventLogRepository
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @KafkaListener(topics = ["membership-update-request"], groupId = "membership-service")
    fun handleMembershipUpdateRequest(event: MembershipUpdateRequestEvent) {
        try {
            if (membershipEventLogRepository.existsByEventId(event.eventId)) {
                log.info("이미 처리된 멤버십 이벤트: eventId={}", event.eventId)
                return
            }

            membershipCommandService.addPurchaseAmount(event.userId, event.amount)

            try {
                membershipEventLogRepository.save(
                    MembershipEventLog(
                        eventId = event.eventId,
                        paymentId = event.paymentId,
                        orderId = event.orderId
                    )
                )
            } catch (e: DataIntegrityViolationException) {
                log.info("이미 처리된 멤버십 이벤트: eventId={}", event.eventId)
                return
            }

            log.info("멤버십 업데이트 완료: userId={}, amount={}", event.userId, event.amount)
        } catch (e: Exception) {
            log.error("멤버십 업데이트 실패: eventId={}, userId={}, 오류={}", event.eventId, event.userId, e.message)
            throw e
        }
    }
}
