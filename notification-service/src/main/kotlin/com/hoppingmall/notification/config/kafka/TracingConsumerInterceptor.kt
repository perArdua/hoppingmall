package com.hoppingmall.notification.config.kafka

import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.MDC
import org.springframework.kafka.listener.RecordInterceptor
import java.util.UUID

class TracingConsumerInterceptor : RecordInterceptor<String, Any> {

    override fun intercept(record: ConsumerRecord<String, Any>, consumer: Consumer<String, Any>): ConsumerRecord<String, Any> {
        val traceId = extractHeader(record, TRACE_ID_HEADER)
            ?: UUID.randomUUID().toString().replace("-", "").take(16)
        MDC.put(TRACE_ID_KEY, traceId)
        extractHeader(record, USER_ID_HEADER)?.let { MDC.put(USER_ID_KEY, it) }
        extractHeader(record, SERVICE_HEADER)?.let { MDC.put(SERVICE_KEY, it) }
        return record
    }

    override fun afterRecord(record: ConsumerRecord<String, Any>, consumer: Consumer<String, Any>) {
        MDC.remove(TRACE_ID_KEY)
        MDC.remove(USER_ID_KEY)
        MDC.remove(SERVICE_KEY)
    }

    private fun extractHeader(record: ConsumerRecord<String, Any>, headerName: String): String? {
        val header = record.headers().lastHeader(headerName) ?: return null
        return String(header.value(), Charsets.UTF_8)
    }

    companion object {
        const val TRACE_ID_KEY = "traceId"
        const val TRACE_ID_HEADER = "X-Trace-Id"
        const val USER_ID_KEY = "userId"
        const val USER_ID_HEADER = "X-User-Id"
        const val SERVICE_KEY = "service"
        const val SERVICE_HEADER = "X-Service-Name"
    }
}
