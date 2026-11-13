package com.hoppingmall.dlq.reprocess

import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Component
class DLQReprocessors(
    reprocessors: List<DLQMessageReprocessor> = emptyList()
) {

    private val byTarget: Map<Pair<String, String>, DLQMessageReprocessor> =
        reprocessors.groupBy { it.topic to it.consumerGroup }
            .mapValues { (target, registered) ->
                check(registered.size == 1) {
                    "같은 토픽·소비 그룹에 DLQ 처리기가 여럿 등록됨: topic=${target.first}, group=${target.second}"
                }
                registered.single()
            }

    fun supports(topic: String, consumerGroup: String?): Boolean =
        consumerGroup != null && byTarget.containsKey(topic to consumerGroup)

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun reprocess(topic: String, consumerGroup: String, key: String?, value: String?) {
        val reprocessor = byTarget[topic to consumerGroup]
            ?: throw IllegalStateException("DLQ 처리기 없음: topic=$topic, group=$consumerGroup")
        reprocessor.reprocess(key, value)
    }
}
