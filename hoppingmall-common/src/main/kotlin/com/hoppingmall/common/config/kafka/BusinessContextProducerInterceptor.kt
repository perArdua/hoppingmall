package com.hoppingmall.common.config.kafka

import com.hoppingmall.common.config.MdcFilter
import org.apache.kafka.clients.producer.ProducerInterceptor
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.slf4j.MDC

class BusinessContextProducerInterceptor : ProducerInterceptor<String, Any> {

    override fun onSend(record: ProducerRecord<String, Any>): ProducerRecord<String, Any> {
        addHeaderFromMdc(record, MdcFilter.USER_ID_KEY, MdcFilter.USER_ID_HEADER)
        addHeaderFromMdc(record, MdcFilter.SERVICE_KEY, MdcFilter.SERVICE_HEADER)
        addHeaderFromMdc(record, MdcFilter.GLOBAL_TRACE_ID_KEY, MdcFilter.GLOBAL_TRACE_ID_HEADER)
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
}
