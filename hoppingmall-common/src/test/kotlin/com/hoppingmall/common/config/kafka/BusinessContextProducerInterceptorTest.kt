package com.hoppingmall.common.config.kafka

import com.hoppingmall.common.config.MdcFilter
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.header.internals.RecordHeaders
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.slf4j.MDC

@DisplayName("BusinessContextProducerInterceptor 단위 테스트")
@DisplayNameGeneration(ReplaceUnderscores::class)
class BusinessContextProducerInterceptorTest {

    private val interceptor = BusinessContextProducerInterceptor()

    @AfterEach
    fun tearDown() {
        MDC.clear()
    }

    @Test
    fun MDC에_값이_있으면_모든_헤더를_전파한다() {
        MDC.put(MdcFilter.USER_ID_KEY, "42")
        MDC.put(MdcFilter.SERVICE_KEY, "payment-service")
        MDC.put(MdcFilter.GLOBAL_TRACE_ID_KEY, "abc123")

        val record = ProducerRecord<String, Any>("test-topic", "key", "value")
        val result = interceptor.onSend(record)

        assertThat(headerValue(result, MdcFilter.USER_ID_HEADER)).isEqualTo("42")
        assertThat(headerValue(result, MdcFilter.SERVICE_HEADER)).isEqualTo("payment-service")
        assertThat(headerValue(result, MdcFilter.GLOBAL_TRACE_ID_HEADER)).isEqualTo("abc123")
    }

    @Test
    fun MDC에_값이_없으면_헤더를_추가하지_않는다() {
        val record = ProducerRecord<String, Any>("test-topic", "key", "value")
        val result = interceptor.onSend(record)

        assertThat(result.headers().toList()).isEmpty()
    }

    @Test
    fun MDC에_일부_값만_있으면_해당_헤더만_전파한다() {
        MDC.put(MdcFilter.USER_ID_KEY, "42")

        val record = ProducerRecord<String, Any>("test-topic", "key", "value")
        val result = interceptor.onSend(record)

        assertThat(headerValue(result, MdcFilter.USER_ID_HEADER)).isEqualTo("42")
        assertThat(result.headers().lastHeader(MdcFilter.SERVICE_HEADER)).isNull()
        assertThat(result.headers().lastHeader(MdcFilter.GLOBAL_TRACE_ID_HEADER)).isNull()
    }

    private fun headerValue(record: ProducerRecord<String, Any>, key: String): String? {
        val header = record.headers().lastHeader(key) ?: return null
        return String(header.value(), Charsets.UTF_8)
    }
}
