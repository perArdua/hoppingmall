package com.hoppingmall.mall.global.common.config.kafka

import org.apache.kafka.clients.producer.ProducerRecord
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.slf4j.MDC

@DisplayName("TracingProducerInterceptor")
@DisplayNameGeneration(ReplaceUnderscores::class)
class TracingProducerInterceptorTest {

    private val interceptor = TracingProducerInterceptor()

    @AfterEach
    fun tearDown() {
        MDC.clear()
    }

    @Nested
    @DisplayName("onSend")
    inner class OnSend {

        @Test
        fun MDC에_traceId가_있으면_Kafka_헤더에_추가된다() {
            MDC.put("traceId", "abc123")
            val record = ProducerRecord<String, Any>("test-topic", "key", "value")

            val result = interceptor.onSend(record)

            val header = result.headers().lastHeader("X-Trace-Id")
            assertThat(header).isNotNull
            assertThat(String(header.value(), Charsets.UTF_8)).isEqualTo("abc123")
        }

        @Test
        fun MDC에_traceId가_없으면_헤더를_추가하지_않는다() {
            val record = ProducerRecord<String, Any>("test-topic", "key", "value")

            val result = interceptor.onSend(record)

            val header = result.headers().lastHeader("X-Trace-Id")
            assertThat(header).isNull()
        }

        @Test
        fun MDC에_userId가_있으면_Kafka_헤더에_추가된다() {
            MDC.put("userId", "42")
            val record = ProducerRecord<String, Any>("test-topic", "key", "value")

            val result = interceptor.onSend(record)

            val header = result.headers().lastHeader("X-User-Id")
            assertThat(header).isNotNull
            assertThat(String(header.value(), Charsets.UTF_8)).isEqualTo("42")
        }

        @Test
        fun MDC에_service가_있으면_Kafka_헤더에_추가된다() {
            MDC.put("service", "monolith")
            val record = ProducerRecord<String, Any>("test-topic", "key", "value")

            val result = interceptor.onSend(record)

            val header = result.headers().lastHeader("X-Service-Name")
            assertThat(header).isNotNull
            assertThat(String(header.value(), Charsets.UTF_8)).isEqualTo("monolith")
        }
    }
}
