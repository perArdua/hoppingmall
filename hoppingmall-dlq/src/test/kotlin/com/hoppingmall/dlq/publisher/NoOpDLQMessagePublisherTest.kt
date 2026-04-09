package com.hoppingmall.dlq.publisher

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test

@DisplayName("NoOpDLQMessagePublisher")
@DisplayNameGeneration(ReplaceUnderscores::class)
class NoOpDLQMessagePublisherTest {

    private val publisher = NoOpDLQMessagePublisher()

    @Test
    fun publish_호출하면_false를_반환한다() {
        val result = publisher.publish("test-topic", "test-key", "test-value")

        assertThat(result).isFalse()
    }

    @Test
    fun publish_value가_null이어도_false를_반환한다() {
        val result = publisher.publish("test-topic", "test-key", null)

        assertThat(result).isFalse()
    }
}
