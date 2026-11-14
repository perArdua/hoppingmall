package com.hoppingmall.user.consumer

import com.hoppingmall.common.KafkaTopics
import com.hoppingmall.dlq.reprocess.DLQMessageReprocessor
import org.springframework.stereotype.Component

@Component
class MembershipDLQReprocessor(
    private val membershipEventConsumer: MembershipEventConsumer
) : DLQMessageReprocessor {

    override val topic: String = KafkaTopics.MEMBERSHIP_UPDATE_REQUEST

    override val consumerGroup: String = MembershipEventConsumer.CONSUMER_GROUP

    override fun reprocess(key: String?, value: String?) {
        val body = requireNotNull(value) { "DLQ 메시지 본문 없음: topic=$topic" }
        membershipEventConsumer.handleMembershipUpdateRequest(body)
    }
}
