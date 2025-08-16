package com.hoppingmall.mall.global.common.domain.repository

import com.hoppingmall.mall.global.common.domain.DLQMessage
import com.hoppingmall.mall.global.common.domain.DLQStatus
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import org.springframework.data.domain.PageRequest

@DataJpaTest
@DisplayName("DLQMessageRepository")
@DisplayNameGeneration(ReplaceUnderscores::class)
class DLQMessageRepositoryTest {

    @Autowired
    private lateinit var dlqMessageRepository: DLQMessageRepository
    
    @Autowired
    private lateinit var testEntityManager: TestEntityManager

    @Nested
    @DisplayName("findByOriginalTopicOrderByCreatedAtDesc")
    inner class FindByOriginalTopicOrderByCreatedAtDesc {
        
        @Test
        fun 특정_토픽의_메시지들을_생성일시_내림차순으로_조회한다() {
            // given
            val topic = "payment"
            val dlqMessage1 = createAndSaveDLQMessage(topic = topic)
            Thread.sleep(10)
            val dlqMessage2 = createAndSaveDLQMessage(topic = topic)
            createAndSaveDLQMessage(topic = "order")
            
            val pageable = PageRequest.of(0, 10)
            
            // when
            val result = dlqMessageRepository.findByOriginalTopicOrderByCreatedAtDesc(topic, pageable)
            
            // then
            assertEquals(2, result.content.size)
            assertTrue(result.content[0].createdAt.isAfter(result.content[1].createdAt))
        }
    }
    
    @Nested
    @DisplayName("findByStatusOrderByCreatedAtDesc")
    inner class FindByStatusOrderByCreatedAtDesc {
        
        @Test
        fun 특정_상태의_메시지들을_생성일시_내림차순으로_조회한다() {
            // given
            val pendingMessage1 = createAndSaveDLQMessage(status = DLQStatus.PENDING)
            Thread.sleep(10)
            val pendingMessage2 = createAndSaveDLQMessage(status = DLQStatus.PENDING)
            createAndSaveDLQMessage(status = DLQStatus.PROCESSED)
            
            val pageable = PageRequest.of(0, 10)
            
            // when
            val result = dlqMessageRepository.findByStatusOrderByCreatedAtDesc(DLQStatus.PENDING, pageable)
            
            // then
            assertEquals(2, result.content.size)
            assertTrue(result.content[0].createdAt.isAfter(result.content[1].createdAt))
        }
    }
    
    @Nested
    @DisplayName("findByOriginalTopicAndStatusOrderByCreatedAtDesc")
    inner class FindByOriginalTopicAndStatusOrderByCreatedAtDesc {
        
        @Test
        fun 특정_토픽과_상태의_메시지들을_조회한다() {
            // given
            val topic = "payment"
            val status = DLQStatus.PENDING
            
            createAndSaveDLQMessage(topic = topic, status = status)
            createAndSaveDLQMessage(topic = topic, status = DLQStatus.PROCESSED)
            createAndSaveDLQMessage(topic = "order", status = status)
            
            val pageable = PageRequest.of(0, 10)
            
            // when
            val result = dlqMessageRepository.findByOriginalTopicAndStatusOrderByCreatedAtDesc(topic, status, pageable)
            
            // then
            assertEquals(1, result.content.size)
            assertEquals(topic, result.content[0].originalTopic)
            assertEquals(status, result.content[0].status)
        }
    }
    
    @Nested
    @DisplayName("countByOriginalTopic")
    inner class CountByOriginalTopic {
        
        @Test
        fun 특정_토픽의_메시지_개수를_반환한다() {
            // given
            val topic = "payment"
            createAndSaveDLQMessage(topic = topic)
            createAndSaveDLQMessage(topic = topic)
            createAndSaveDLQMessage(topic = "order")
            
            // when
            val count = dlqMessageRepository.countByOriginalTopic(topic)
            
            // then
            assertEquals(2, count)
        }
    }
    
    @Nested
    @DisplayName("countByStatus")
    inner class CountByStatus {
        
        @Test
        fun 특정_상태의_메시지_개수를_반환한다() {
            // given
            createAndSaveDLQMessage(status = DLQStatus.PENDING)
            createAndSaveDLQMessage(status = DLQStatus.PENDING)
            createAndSaveDLQMessage(status = DLQStatus.PROCESSED)
            
            // when
            val count = dlqMessageRepository.countByStatus(DLQStatus.PENDING)
            
            // then
            assertEquals(2, count)
        }
    }
    
    @Nested
    @DisplayName("findRetryableMessages")
    inner class FindRetryableMessages {
        
        @Test
        fun 재시도_가능한_메시지들을_조회한다() {
            // given
            val retryableMessage1 = createAndSaveDLQMessage(
                status = DLQStatus.PENDING,
                retryCount = 1
            )
            val retryableMessage2 = createAndSaveDLQMessage(
                status = DLQStatus.PENDING,
                retryCount = 2
            )
            createAndSaveDLQMessage(
                status = DLQStatus.PENDING,
                retryCount = 3
            )
            createAndSaveDLQMessage(status = DLQStatus.PROCESSED)
            
            val pageable = PageRequest.of(0, 10)
            
            // when
            val result = dlqMessageRepository.findRetryableMessages(DLQStatus.PENDING, 3, pageable)
            
            // then
            assertEquals(2, result.content.size)
            assertTrue(result.content.all { it.retryCount < 3 })
            assertTrue(result.content.all { it.status == DLQStatus.PENDING })
        }
    }
    
    @Nested
    @DisplayName("existsByOriginalTopicAndOriginalPartitionAndOriginalOffset")
    inner class ExistsByOriginalTopicAndOriginalPartitionAndOriginalOffset {
        
        @Test
        fun 동일한_토픽_파티션_오프셋의_메시지가_존재하면_true를_반환한다() {
            // given
            val topic = "payment"
            val partition = 0
            val offset = 12345L
            
            createAndSaveDLQMessage(
                topic = topic,
                partition = partition,
                offset = offset
            )
            
            // when
            val exists = dlqMessageRepository.existsByOriginalTopicAndOriginalPartitionAndOriginalOffset(
                topic, partition, offset
            )
            
            // then
            assertTrue(exists)
        }
        
        @Test
        fun 동일한_메시지가_존재하지_않으면_false를_반환한다() {
            // given
            createAndSaveDLQMessage(topic = "payment", partition = 0, offset = 12345L)
            
            // when
            val exists = dlqMessageRepository.existsByOriginalTopicAndOriginalPartitionAndOriginalOffset(
                "payment", 0, 12346L
            )
            
            // then
            assertFalse(exists)
        }
    }
    
    @Nested
    @DisplayName("getDLQStatsByTopic")
    inner class GetDLQStatsByTopic {
        
        @Test
        fun 토픽별_DLQ_통계를_반환한다() {
            // given
            val paymentTopic = "payment"
            val orderTopic = "order"
            
            createAndSaveDLQMessage(topic = paymentTopic, status = DLQStatus.PENDING)
            createAndSaveDLQMessage(topic = paymentTopic, status = DLQStatus.PROCESSED)
            createAndSaveDLQMessage(topic = paymentTopic, status = DLQStatus.FAILED)
            
            createAndSaveDLQMessage(topic = orderTopic, status = DLQStatus.PENDING)
            createAndSaveDLQMessage(topic = orderTopic, status = DLQStatus.PENDING)
            
            // when
            val stats = dlqMessageRepository.getDLQStatsByTopic()
            
            // then
            assertEquals(2, stats.size)
            
            val paymentStats = stats.find { it.topic == paymentTopic }!!
            assertEquals(3, paymentStats.totalCount)
            assertEquals(1, paymentStats.pendingCount)
            assertEquals(1, paymentStats.processedCount)
            assertEquals(1, paymentStats.failedCount)
            
            val orderStats = stats.find { it.topic == orderTopic }!!
            assertEquals(2, orderStats.totalCount)
            assertEquals(2, orderStats.pendingCount)
            assertEquals(0, orderStats.processedCount)
            assertEquals(0, orderStats.failedCount)
        }
    }
    
    private fun createAndSaveDLQMessage(
        topic: String = "test-topic",
        partition: Int = 0,
        offset: Long = System.nanoTime(),
        key: String? = "test-key",
        value: String? = "test-value",
        exception: String? = "Test exception",
        timestamp: Long = System.currentTimeMillis(),
        status: DLQStatus = DLQStatus.PENDING,
        retryCount: Int = 0
    ): DLQMessage {
        val dlqMessage = DLQMessage(
            originalTopic = topic,
            originalPartition = partition,
            originalOffset = offset,
            originalKey = key,
            originalValue = value,
            exceptionMessage = exception,
            errorTimestamp = timestamp
        ).apply {
            this.status = status
            this.retryCount = retryCount
        }
        
        return testEntityManager.persistAndFlush(dlqMessage)
    }
}