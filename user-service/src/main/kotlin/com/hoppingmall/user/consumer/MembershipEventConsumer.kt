package com.hoppingmall.user.consumer

import com.hoppingmall.common.KafkaTopics
import com.hoppingmall.common.consumer.executeIdempotently
import com.hoppingmall.user.domain.MembershipEventLog
import com.hoppingmall.user.domain.repository.MembershipEventLogRepository
import com.hoppingmall.user.service.MembershipCommandService
import org.slf4j.LoggerFactory
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

    @KafkaListener(topics = [KafkaTopics.MEMBERSHIP_UPDATE_REQUEST], groupId = "membership-service")
    fun handleMembershipUpdateRequest(event: MembershipUpdateRequestEvent) {
        executeIdempotently(
            eventId = event.eventId,
            eventDescription = "멤버십",
            logger = log,
            existsCheck = { membershipEventLogRepository.existsByEventId(event.eventId) }
        ) {
            membershipCommandService.addPurchaseAmount(event.userId, event.amount)

            membershipEventLogRepository.save(
                MembershipEventLog(
                    eventId = event.eventId,
                    paymentId = event.paymentId,
                    orderId = event.orderId
                )
            )

            log.info("멤버십 업데이트 완료: userId={}, amount={}", event.userId, event.amount)
        }
    }
}
