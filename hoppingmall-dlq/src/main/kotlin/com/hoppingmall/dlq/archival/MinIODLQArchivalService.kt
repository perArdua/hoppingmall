package com.hoppingmall.dlq.archival

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.hoppingmall.dlq.domain.DLQMessage
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.stereotype.Component
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Component
@ConditionalOnBean(S3Client::class)
class MinIODLQArchivalService(
    private val s3Client: S3Client,
    private val dlqArchivalProperties: DLQArchivalProperties
) : DLQArchivalService {

    private val log = LoggerFactory.getLogger(javaClass)

    private val objectMapper = ObjectMapper().apply {
        registerModule(JavaTimeModule())
        disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    }

    override fun archive(dlqMessage: DLQMessage): Boolean {
        return try {
            val key = buildObjectKey(dlqMessage)
            val json = serializeToJson(dlqMessage)

            s3Client.putObject(
                PutObjectRequest.builder()
                    .bucket(dlqArchivalProperties.bucket)
                    .key(key)
                    .contentType("application/json")
                    .build(),
                RequestBody.fromString(json)
            )

            log.info("DLQ 메시지 아카이빙 완료: id={}, key={}", dlqMessage.id, key)
            true
        } catch (e: Exception) {
            log.error("DLQ 메시지 아카이빙 실패: id={}, error={}", dlqMessage.id, e.message, e)
            false
        }
    }

    override fun archiveBatch(dlqMessages: List<DLQMessage>): Int {
        var successCount = 0
        dlqMessages.forEach { message ->
            if (archive(message)) {
                successCount++
            }
        }
        return successCount
    }

    private fun buildObjectKey(dlqMessage: DLQMessage): String {
        val timestamp = Instant.ofEpochMilli(dlqMessage.errorTimestamp)
            .atZone(ZoneId.systemDefault())
        val datePath = timestamp.format(DateTimeFormatter.ofPattern("yyyy/MM/dd"))
        return "dlq-archive/${dlqMessage.originalTopic}/$datePath/${dlqMessage.id}.json"
    }

    private fun serializeToJson(dlqMessage: DLQMessage): String {
        val archiveData = mapOf(
            "id" to dlqMessage.id,
            "originalTopic" to dlqMessage.originalTopic,
            "originalPartition" to dlqMessage.originalPartition,
            "originalOffset" to dlqMessage.originalOffset,
            "originalKey" to dlqMessage.originalKey,
            "originalValue" to dlqMessage.originalValue,
            "exceptionMessage" to dlqMessage.exceptionMessage,
            "errorTimestamp" to dlqMessage.errorTimestamp,
            "status" to dlqMessage.status.name,
            "retryCount" to dlqMessage.retryCount,
            "lastRetryAt" to dlqMessage.lastRetryAt,
            "processedAt" to dlqMessage.processedAt,
            "notes" to dlqMessage.notes,
            "nextRetryAt" to dlqMessage.nextRetryAt,
            "createdAt" to dlqMessage.createdAt.toString(),
            "updatedAt" to dlqMessage.updatedAt?.toString(),
            "archivedAt" to System.currentTimeMillis()
        )
        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(archiveData)
    }
}
