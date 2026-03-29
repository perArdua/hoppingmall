package com.hoppingmall.payment.config.kafka

import org.apache.kafka.clients.producer.ProducerInterceptor
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.slf4j.MDC

class BusinessContextProducerInterceptor : ProducerInterceptor<String, Any> {

    override fun onSend(record: ProducerRecord<String, Any>): ProducerRecord<String, Any> {
        addHeaderFromMdc(record, USER_ID_KEY, USER_ID_HEADER)
        addHeaderFromMdc(record, SERVICE_KEY, SERVICE_HEADER)
        addHeaderFromMdc(record, GLOBAL_TRACE_ID_KEY, GLOBAL_TRACE_ID_HEADER)
        return record
    }

    private fun addHeaderFromMdc(record: ProducerRecord<String, Any>, mdcKey: String, headerName: String) {
        val value = MDC.get(mdcKey)
        if (value != null) {
            record.headers().add(headerName, value.toByteArray(Charsets.UTF_8))
        }
    }

    override fun onAcknowledgement(metadata: RecordMetadata?, exception: Exception?) {}

    override fun close() {}

    override fun configure(configs: MutableMap<String, *>?) {}

    companion object {
        const val USER_ID_KEY = "userId"
        const val USER_ID_HEADER = "X-User-Id"
        const val SERVICE_KEY = "service"
        const val SERVICE_HEADER = "X-Service-Name"
        const val GLOBAL_TRACE_ID_KEY = "globalTraceId"
        const val GLOBAL_TRACE_ID_HEADER = "X-Global-Trace-Id"
    }
}
