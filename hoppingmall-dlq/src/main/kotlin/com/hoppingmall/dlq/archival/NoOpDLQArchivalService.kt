package com.hoppingmall.dlq.archival

import com.hoppingmall.dlq.domain.DLQMessage
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.stereotype.Component

@Component
@ConditionalOnMissingBean(MinIODLQArchivalService::class)
class NoOpDLQArchivalService : DLQArchivalService {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun archive(dlqMessage: DLQMessage): Boolean {
        log.warn("S3Client 미설정으로 DLQ 아카이빙 불가: topic={}, id={}", dlqMessage.originalTopic, dlqMessage.id)
        return false
    }

    override fun archiveBatch(dlqMessages: List<DLQMessage>): Int {
        if (dlqMessages.isNotEmpty()) {
            log.warn("S3Client 미설정으로 DLQ 배치 아카이빙 불가: {} 건", dlqMessages.size)
        }
        return 0
    }
}
