package com.hoppingmall.user.consumer

import com.fasterxml.jackson.databind.JsonNode
import com.hoppingmall.common.KafkaTopics
import com.hoppingmall.common.consumer.executeIdempotently
import com.hoppingmall.common.event.KafkaEventMapper
import com.hoppingmall.user.domain.MembershipEventLog
import com.hoppingmall.user.domain.enums.MembershipEventType
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
    private val membershipEventLogRepository: MembershipEventLogRepository,
    private val kafkaEventMapper: KafkaEventMapper
) {

    companion object {
        const val CONSUMER_GROUP = "membership-service"
        private const val REVERT_EVENT_TYPE = "MembershipRevertRequested"
    }

    private val log = LoggerFactory.getLogger(javaClass)

    @KafkaListener(topics = [KafkaTopics.MEMBERSHIP_UPDATE_REQUEST], groupId = CONSUMER_GROUP)
    fun handleMembershipUpdateRequest(message: String) {
        val node = kafkaEventMapper.readValue(message, JsonNode::class.java)
        if (node.get("eventType")?.asText() == REVERT_EVENT_TYPE) {
            revert(kafkaEventMapper.treeToValue(node, MembershipRevertRequestEvent::class.java))
            return
        }
        val event = kafkaEventMapper.treeToValue(node, MembershipUpdateRequestEvent::class.java)
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

    private fun revert(event: MembershipRevertRequestEvent) {
        executeIdempotently(
            eventId = event.eventId,
            eventDescription = "멤버십 되돌리기",
            logger = log,
            existsCheck = { membershipEventLogRepository.existsByEventId(event.eventId) }
        ) {
            check(membershipEventLogRepository.existsPurchaseByPaymentId(event.paymentId, MembershipEventType.PURCHASE)) {
                "같은 결제의 멤버십 더하기 기록이 아직 없어 되돌릴 수 없음: paymentId=${event.paymentId}"
            }

            membershipCommandService.revertPurchaseAmount(event.userId, event.amount)

            membershipEventLogRepository.save(
                MembershipEventLog(
                    eventId = event.eventId,
                    paymentId = event.paymentId,
                    orderId = event.orderId,
                    type = MembershipEventType.REVERT
                )
            )

            log.info(
                "멤버십 되돌리기 완료: userId={}, amount={}, reason={}, refundId={}",
                event.userId, event.amount, event.reason, event.refundId
            )
        }
    }
}
