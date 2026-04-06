package com.hoppingmall.common.config.kafka

import com.hoppingmall.common.config.MdcFilter
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.MDC
import org.springframework.kafka.listener.RecordInterceptor

class BusinessContextConsumerInterceptor : RecordInterceptor<String, Any> {

    override fun intercept(record: ConsumerRecord<String, Any>, consumer: Consumer<String, Any>): ConsumerRecord<String, Any> {
        extractHeader(record, MdcFilter.USER_ID_HEADER)?.let { MDC.put(MdcFilter.USER_ID_KEY, it) }
        extractHeader(record, MdcFilter.SERVICE_HEADER)?.let { MDC.put(MdcFilter.SERVICE_KEY, it) }
        extractHeader(record, MdcFilter.GLOBAL_TRACE_ID_HEADER)?.let { MDC.put(MdcFilter.GLOBAL_TRACE_ID_KEY, it) }
        return record
    }

    override fun afterRecord(record: ConsumerRecord<String, Any>, consumer: Consumer<String, Any>) {
        MDC.remove(MdcFilter.USER_ID_KEY)
        MDC.remove(MdcFilter.SERVICE_KEY)
        MDC.remove(MdcFilter.GLOBAL_TRACE_ID_KEY)
    }

    private fun extractHeader(record: ConsumerRecord<String, Any>, headerName: String): String? {
        val header = record.headers().lastHeader(headerName) ?: return null
        return String(header.value(), Charsets.UTF_8)
    }
}
