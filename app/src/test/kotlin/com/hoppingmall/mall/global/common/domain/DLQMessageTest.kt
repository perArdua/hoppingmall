package com.hoppingmall.mall.global.common.domain

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("DLQMessage")
@DisplayNameGeneration(ReplaceUnderscores::class)
class DLQMessageTest {

    @Nested
    @DisplayName("incrementRetry")
    inner class IncrementRetry {
        
        @Test
        fun 재시도_카운트를_증가시키고_상태를_RETRYING으로_변경한다() {
            // given
            val dlqMessage = createDLQMessage()
            val originalRetryCount = dlqMessage.retryCount
            
            // when
            dlqMessage.incrementRetry()
            
            // then
            assertEquals(originalRetryCount + 1, dlqMessage.retryCount)
            assertEquals(DLQStatus.RETRYING, dlqMessage.status)
            assertNotNull(dlqMessage.lastRetryAt)
        }
        
        @Test
        fun 여러번_호출하면_재시도_카운트가_누적된다() {
            // given
            val dlqMessage = createDLQMessage()
            
            // when
            dlqMessage.incrementRetry()
            dlqMessage.incrementRetry()
            dlqMessage.incrementRetry()
            
            // then
            assertEquals(3, dlqMessage.retryCount)
            assertEquals(DLQStatus.RETRYING, dlqMessage.status)
        }
    }
    
    @Nested
    @DisplayName("markAsProcessed")
    inner class MarkAsProcessed {
        
        @Test
        fun 상태를_PROCESSED로_변경하고_처리_시간을_설정한다() {
            // given
            val dlqMessage = createDLQMessage()
            val notes = "수동 재처리 성공"
            
            // when
            dlqMessage.markAsProcessed(notes)
            
            // then
            assertEquals(DLQStatus.PROCESSED, dlqMessage.status)
            assertEquals(notes, dlqMessage.notes)
            assertNotNull(dlqMessage.processedAt)
        }
        
        @Test
        fun notes_없이도_처리_완료_처리가_가능하다() {
            // given
            val dlqMessage = createDLQMessage()
            
            // when
            dlqMessage.markAsProcessed()
            
            // then
            assertEquals(DLQStatus.PROCESSED, dlqMessage.status)
            assertNull(dlqMessage.notes)
            assertNotNull(dlqMessage.processedAt)
        }
    }
    
    @Nested
    @DisplayName("markAsFailed")
    inner class MarkAsFailed {
        
        @Test
        fun 상태를_FAILED로_변경하고_노트를_설정한다() {
            // given
            val dlqMessage = createDLQMessage()
            val notes = "최대 재시도 횟수 초과"
            
            // when
            dlqMessage.markAsFailed(notes)
            
            // then
            assertEquals(DLQStatus.FAILED, dlqMessage.status)
            assertEquals(notes, dlqMessage.notes)
        }
    }
    
    @Nested
    @DisplayName("getMessageKey")
    inner class GetMessageKey {
        
        @Test
        fun 토픽_파티션_오프셋으로_유니크_키를_생성한다() {
            // given
            val dlqMessage = createDLQMessage(
                topic = "payment",
                partition = 0,
                offset = 12345L
            )
            
            // when
            val messageKey = dlqMessage.getMessageKey()
            
            // then
            assertEquals("payment:0:12345", messageKey)
        }
        
        @Test
        fun 서로_다른_메시지는_다른_키를_가진다() {
            // given
            val dlqMessage1 = createDLQMessage(topic = "payment", partition = 0, offset = 100L)
            val dlqMessage2 = createDLQMessage(topic = "payment", partition = 0, offset = 101L)
            val dlqMessage3 = createDLQMessage(topic = "order", partition = 0, offset = 100L)
            
            // when & then
            assertNotEquals(dlqMessage1.getMessageKey(), dlqMessage2.getMessageKey())
            assertNotEquals(dlqMessage1.getMessageKey(), dlqMessage3.getMessageKey())
        }
    }
    
    @Nested
    @DisplayName("scheduleNextRetry")
    inner class ScheduleNextRetry {

        @Test
        fun 다음_재시도를_스케줄링하고_상태를_PENDING으로_변경한다() {
            // given
            val dlqMessage = createDLQMessage()
            dlqMessage.incrementRetry()
            assertEquals(DLQStatus.RETRYING, dlqMessage.status)

            // when
            dlqMessage.scheduleNextRetry()

            // then
            assertEquals(DLQStatus.PENDING, dlqMessage.status)
            assertNotNull(dlqMessage.nextRetryAt)
        }
    }

    @Nested
    @DisplayName("isNonRetryableException")
    inner class IsNonRetryableException {

        @Test
        fun DeserializationException이_포함된_에러는_비재시도로_판별한다() {
            // given
            val dlqMessage = createDLQMessage(exception = "org.apache.kafka.common.errors.DeserializationException: Error")

            // when & then
            assertTrue(dlqMessage.isNonRetryableException())
        }

        @Test
        fun IllegalArgumentException이_포함된_에러는_비재시도로_판별한다() {
            // given
            val dlqMessage = createDLQMessage(exception = "java.lang.IllegalArgumentException: Invalid value")

            // when & then
            assertTrue(dlqMessage.isNonRetryableException())
        }

        @Test
        fun 일반_런타임_에러는_재시도_가능으로_판별한다() {
            // given
            val dlqMessage = createDLQMessage(exception = "java.net.SocketTimeoutException: Connection timed out")

            // when & then
            assertFalse(dlqMessage.isNonRetryableException())
        }

        @Test
        fun 예외_메시지가_null이면_재시도_가능으로_판별한다() {
            // given
            val dlqMessage = createDLQMessage(exception = null)

            // when & then
            assertFalse(dlqMessage.isNonRetryableException())
        }
    }

    @Nested
    @DisplayName("calculateNextRetryAt")
    inner class CalculateNextRetryAt {

        @Test
        fun 재시도_횟수에_따라_백오프_간격이_증가한다() {
            // given & when
            val before = System.currentTimeMillis()
            val firstRetry = DLQMessage.calculateNextRetryAt(0)
            val secondRetry = DLQMessage.calculateNextRetryAt(1)
            val thirdRetry = DLQMessage.calculateNextRetryAt(2)

            // then
            assertTrue(firstRetry >= before + 60_000L)
            assertTrue(secondRetry >= before + 300_000L)
            assertTrue(thirdRetry >= before + 1_800_000L)
        }

        @Test
        fun 최대_인덱스를_초과해도_마지막_간격을_사용한다() {
            // given & when
            val before = System.currentTimeMillis()
            val retryAt = DLQMessage.calculateNextRetryAt(10)

            // then
            assertTrue(retryAt >= before + 1_800_000L)
        }
    }

    @Nested
    @DisplayName("incrementRetry with nextRetryAt")
    inner class IncrementRetryWithNextRetryAt {

        @Test
        fun 재시도_증가_시_nextRetryAt도_설정된다() {
            // given
            val dlqMessage = createDLQMessage()

            // when
            dlqMessage.incrementRetry()

            // then
            assertNotNull(dlqMessage.nextRetryAt)
            assertTrue(dlqMessage.nextRetryAt!! > System.currentTimeMillis())
        }
    }

    private fun createDLQMessage(
        topic: String = "test-topic",
        partition: Int = 0,
        offset: Long = 1000L,
        key: String? = "test-key",
        value: String? = "test-value",
        exception: String? = "Test exception",
        timestamp: Long = System.currentTimeMillis()
    ): DLQMessage {
        return DLQMessage(
            originalTopic = topic,
            originalPartition = partition,
            originalOffset = offset,
            originalKey = key,
            originalValue = value,
            exceptionMessage = exception,
            errorTimestamp = timestamp
        )
    }
}