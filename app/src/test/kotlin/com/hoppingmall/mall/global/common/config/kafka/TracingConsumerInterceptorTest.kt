package com.hoppingmall.mall.global.common.config.kafka

import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.header.internals.RecordHeaders
import org.apache.kafka.common.record.TimestampType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.mockito.Mockito.mock
import org.slf4j.MDC

@DisplayName("TracingConsumerInterceptor")
@DisplayNameGeneration(ReplaceUnderscores::class)
class TracingConsumerInterceptorTest {

    private val interceptor = TracingConsumerInterceptor()
    private val consumer = mock<Consumer<String, Any>>()

    @AfterEach
    fun tearDown() {
        MDC.clear()
    }

    private fun createRecord(headers: RecordHeaders = RecordHeaders()): ConsumerRecord<String, Any> {
        return ConsumerRecord("topic", 0, 0L, 0L, TimestampType.CREATE_TIME, 0, 0, "key", "value" as Any, headers, java.util.Optional.empty())
    }

    @Nested
    @DisplayName("intercept")
    inner class Intercept {

        @Test
        fun 헤더에_traceId가_있으면_MDC에_설정된다() {
            val headers = RecordHeaders()
            headers.add("X-Trace-Id", "incoming-trace".toByteArray(Charsets.UTF_8))
            val record = createRecord(headers)

            interceptor.intercept(record, consumer)

            assertThat(MDC.get("traceId")).isEqualTo("incoming-trace")
        }

        @Test
        fun 헤더에_traceId가_없으면_새로_생성하여_MDC에_설정된다() {
            val record = createRecord()

            interceptor.intercept(record, consumer)

            val traceId = MDC.get("traceId")
            assertThat(traceId).isNotNull()
            assertThat(traceId).hasSize(16)
        }

        @Test
        fun 헤더에_userId가_있으면_MDC에_설정된다() {
            val headers = RecordHeaders()
            headers.add("X-Trace-Id", "trace".toByteArray(Charsets.UTF_8))
            headers.add("X-User-Id", "42".toByteArray(Charsets.UTF_8))
            val record = createRecord(headers)

            interceptor.intercept(record, consumer)

            assertThat(MDC.get("userId")).isEqualTo("42")
        }

        @Test
        fun 헤더에_serviceName이_있으면_MDC에_설정된다() {
            val headers = RecordHeaders()
            headers.add("X-Trace-Id", "trace".toByteArray(Charsets.UTF_8))
            headers.add("X-Service-Name", "monolith".toByteArray(Charsets.UTF_8))
            val record = createRecord(headers)

            interceptor.intercept(record, consumer)

            assertThat(MDC.get("service")).isEqualTo("monolith")
        }
    }

    @Nested
    @DisplayName("afterRecord")
    inner class AfterRecord {

        @Test
        fun 처리_완료_후_MDC에서_모든_키가_제거된다() {
            MDC.put("traceId", "some-trace")
            MDC.put("userId", "42")
            MDC.put("service", "monolith")
            val record = createRecord()

            interceptor.afterRecord(record, consumer)

            assertThat(MDC.get("traceId")).isNull()
            assertThat(MDC.get("userId")).isNull()
            assertThat(MDC.get("service")).isNull()
        }
    }
}
