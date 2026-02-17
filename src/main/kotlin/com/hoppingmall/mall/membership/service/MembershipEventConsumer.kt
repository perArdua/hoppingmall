package com.hoppingmall.mall.membership.service

import com.hoppingmall.mall.membership.domain.MembershipEventLog
import com.hoppingmall.mall.membership.domain.repository.MembershipEventLogRepository
import com.hoppingmall.mall.payment.dto.event.MembershipUpdateRequestEvent
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

    @KafkaListener(topics = ["membership-update-request"], groupId = "membership-service")
    fun handleMembershipUpdateRequest(event: MembershipUpdateRequestEvent) {
        try {
            if (membershipEventLogRepository.existsByEventId(event.eventId)) {
                return
            }

            membershipCommandService.addPurchaseAmount(event.userId, event.amount)

            membershipEventLogRepository.save(
                MembershipEventLog(
                    eventId = event.eventId,
                    paymentId = event.paymentId,
                    orderId = event.orderId
                )
            )
        } catch (e: DataIntegrityViolationException) {
            return
        }
    }
}
