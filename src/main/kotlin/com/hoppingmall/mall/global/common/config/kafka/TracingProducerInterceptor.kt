package com.hoppingmall.mall.global.common.config.kafka

import org.apache.kafka.clients.producer.ProducerInterceptor
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.slf4j.MDC

class TracingProducerInterceptor : ProducerInterceptor<String, Any> {

    override fun onSend(record: ProducerRecord<String, Any>): ProducerRecord<String, Any> {
        val traceId = MDC.get(TRACE_ID_KEY)
        if (traceId != null) {
            record.headers().add(TRACE_ID_HEADER, traceId.toByteArray(Charsets.UTF_8))
        }
        return record
    }

    override fun onAcknowledgement(metadata: RecordMetadata?, exception: Exception?) {}

    override fun close() {}

    override fun configure(configs: MutableMap<String, *>?) {}

    companion object {
        const val TRACE_ID_KEY = "traceId"
        const val TRACE_ID_HEADER = "X-Trace-Id"
    }
}
