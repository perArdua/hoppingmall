package com.hoppingmall.mall.global.common.config.kafka

import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.MDC
import org.springframework.kafka.listener.RecordInterceptor
import java.util.UUID

class TracingConsumerInterceptor : RecordInterceptor<String, Any> {

    override fun intercept(record: ConsumerRecord<String, Any>, consumer: Consumer<String, Any>): ConsumerRecord<String, Any> {
        val traceId = extractTraceId(record)
        MDC.put(TRACE_ID_KEY, traceId)
        return record
    }

    override fun afterRecord(record: ConsumerRecord<String, Any>, consumer: Consumer<String, Any>) {
        MDC.remove(TRACE_ID_KEY)
    }

    private fun extractTraceId(record: ConsumerRecord<String, Any>): String {
        val header = record.headers().lastHeader(TRACE_ID_HEADER)
        return if (header != null) {
            String(header.value(), Charsets.UTF_8)
        } else {
            UUID.randomUUID().toString().replace("-", "").take(16)
        }
    }

    companion object {
        const val TRACE_ID_KEY = "traceId"
        const val TRACE_ID_HEADER = "X-Trace-Id"
    }
}
