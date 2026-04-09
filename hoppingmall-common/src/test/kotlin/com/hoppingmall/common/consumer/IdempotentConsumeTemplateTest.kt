package com.hoppingmall.common.consumer

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException

@DisplayName("IdempotentConsumeTemplate 단위 테스트")
@DisplayNameGeneration(ReplaceUnderscores::class)
class IdempotentConsumeTemplateTest {

    private val logger = LoggerFactory.getLogger(javaClass)

    @Test
    fun 중복되지_않은_이벤트는_액션을_실행한다() {
        var executed = false

        executeIdempotently(
            eventId = "event-1",
            eventDescription = "테스트",
            logger = logger,
            existsCheck = { false },
            action = { executed = true }
        )

        assertThat(executed).isTrue()
    }

    @Test
    fun 이미_처리된_이벤트는_액션을_실행하지_않는다() {
        var executed = false

        executeIdempotently(
            eventId = "event-1",
            eventDescription = "테스트",
            logger = logger,
            existsCheck = { true },
            action = { executed = true }
        )

        assertThat(executed).isFalse()
    }

    @Test
    fun DataIntegrityViolationException_발생_시_이미_처리된_것으로_간주한다() {
        var actionCalled = false

        executeIdempotently(
            eventId = "event-1",
            eventDescription = "테스트",
            logger = logger,
            existsCheck = { false },
            action = {
                actionCalled = true
                throw DataIntegrityViolationException("Duplicate entry")
            }
        )

        assertThat(actionCalled).isTrue()
    }

    @Test
    fun 일반_Exception_발생_시_예외를_다시_던진다() {
        assertThatThrownBy {
            executeIdempotently(
                eventId = "event-1",
                eventDescription = "테스트",
                logger = logger,
                existsCheck = { false },
                action = { throw IllegalStateException("처리 실패") }
            )
        }.isInstanceOf(IllegalStateException::class.java)
            .hasMessage("처리 실패")
    }

    @Test
    fun RuntimeException_발생_시_예외를_다시_던진다() {
        assertThatThrownBy {
            executeIdempotently(
                eventId = "event-2",
                eventDescription = "결제",
                logger = logger,
                existsCheck = { false },
                action = { throw RuntimeException("런타임 오류") }
            )
        }.isInstanceOf(RuntimeException::class.java)
            .hasMessage("런타임 오류")
    }

    @Test
    fun existsCheck에서_예외가_발생하면_그대로_전파된다() {
        assertThatThrownBy {
            executeIdempotently(
                eventId = "event-3",
                eventDescription = "포인트",
                logger = logger,
                existsCheck = { throw RuntimeException("DB 연결 실패") },
                action = { }
            )
        }.isInstanceOf(RuntimeException::class.java)
            .hasMessage("DB 연결 실패")
    }
}
