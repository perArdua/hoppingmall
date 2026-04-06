package com.hoppingmall.common.config.kafka

import com.hoppingmall.common.config.MdcFilter
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.header.internals.RecordHeaders
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.apache.kafka.clients.consumer.Consumer
import org.slf4j.MDC

@DisplayName("BusinessContextConsumerInterceptor 단위 테스트")
@DisplayNameGeneration(ReplaceUnderscores::class)
class BusinessContextConsumerInterceptorTest {

    private val interceptor = BusinessContextConsumerInterceptor()

    @Suppress("UNCHECKED_CAST")
    private val consumer = mock(Consumer::class.java) as Consumer<String, Any>

    @AfterEach
    fun tearDown() {
        MDC.clear()
    }

    @Test
    fun 레코드_헤더에서_MDC로_컨텍스트를_복원한다() {
        val headers = RecordHeaders()
        headers.add(MdcFilter.USER_ID_HEADER, "42".toByteArray())
        headers.add(MdcFilter.SERVICE_HEADER, "payment-service".toByteArray())
        headers.add(MdcFilter.GLOBAL_TRACE_ID_HEADER, "abc123".toByteArray())
        val record = ConsumerRecord("test-topic", 0, 0L, "key", "value" as Any)
        headers.forEach { record.headers().add(it) }

        interceptor.intercept(record, consumer)

        assertThat(MDC.get(MdcFilter.USER_ID_KEY)).isEqualTo("42")
        assertThat(MDC.get(MdcFilter.SERVICE_KEY)).isEqualTo("payment-service")
        assertThat(MDC.get(MdcFilter.GLOBAL_TRACE_ID_KEY)).isEqualTo("abc123")
    }

    @Test
    fun afterRecord_호출_시_MDC를_정리한다() {
        MDC.put(MdcFilter.USER_ID_KEY, "42")
        MDC.put(MdcFilter.SERVICE_KEY, "payment-service")
        MDC.put(MdcFilter.GLOBAL_TRACE_ID_KEY, "abc123")

        val record = ConsumerRecord("test-topic", 0, 0L, "key", "value" as Any)
        interceptor.afterRecord(record, consumer)

        assertThat(MDC.get(MdcFilter.USER_ID_KEY)).isNull()
        assertThat(MDC.get(MdcFilter.SERVICE_KEY)).isNull()
        assertThat(MDC.get(MdcFilter.GLOBAL_TRACE_ID_KEY)).isNull()
    }

    @Test
    fun 헤더가_없으면_MDC에_설정하지_않는다() {
        val record = ConsumerRecord("test-topic", 0, 0L, "key", "value" as Any)

        interceptor.intercept(record, consumer)

        assertThat(MDC.get(MdcFilter.USER_ID_KEY)).isNull()
        assertThat(MDC.get(MdcFilter.SERVICE_KEY)).isNull()
        assertThat(MDC.get(MdcFilter.GLOBAL_TRACE_ID_KEY)).isNull()
    }

    @Test
    fun 일부_헤더만_있으면_해당_값만_MDC에_설정한다() {
        val record = ConsumerRecord("test-topic", 0, 0L, "key", "value" as Any)
        record.headers().add(MdcFilter.USER_ID_HEADER, "42".toByteArray())

        interceptor.intercept(record, consumer)

        assertThat(MDC.get(MdcFilter.USER_ID_KEY)).isEqualTo("42")
        assertThat(MDC.get(MdcFilter.SERVICE_KEY)).isNull()
        assertThat(MDC.get(MdcFilter.GLOBAL_TRACE_ID_KEY)).isNull()
    }
}
