package com.hoppingmall.dlq.service

import com.hoppingmall.dlq.archival.NoOpDLQArchivalService
import com.hoppingmall.dlq.domain.DLQMessage
import com.hoppingmall.dlq.domain.DLQStatus
import com.hoppingmall.dlq.domain.DeadLetterMessage
import com.hoppingmall.dlq.domain.repository.DLQMessageRepository
import com.hoppingmall.dlq.metrics.NoOpDLQMetrics
import com.hoppingmall.dlq.publisher.DLQMessagePublisher
import com.hoppingmall.dlq.publisher.NoOpDLQMessagePublisher
import com.hoppingmall.dlq.reprocess.DLQMessageReprocessor
import com.hoppingmall.dlq.reprocess.DLQReprocessors
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.data.repository.findByIdOrNull
import org.springframework.test.context.ContextConfiguration
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.util.concurrent.CopyOnWriteArrayList

@DisplayName("DLQ 실패한 소비 그룹의 처리기로 직접 재처리")
@DisplayNameGeneration(ReplaceUnderscores::class)
@DataJpaTest(properties = ["spring.datasource.url=jdbc:h2:mem:dlq-direct-reprocess;LOCK_TIMEOUT=10000;DB_CLOSE_DELAY=-1"])
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = [DLQDirectReprocessTest.TestConfig::class])
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DLQDirectReprocessTest {

    @Autowired
    @Qualifier("kafkaService")
    private lateinit var kafkaService: DLQCommandService

    @Autowired
    @Qualifier("noKafkaService")
    private lateinit var noKafkaService: DLQCommandService

    @Autowired
    private lateinit var dlqMessageRepository: DLQMessageRepository

    @Autowired
    private lateinit var publisher: RecordingPublisher

    @Autowired
    @Qualifier("sagaReprocessor")
    private lateinit var sagaReprocessor: RecordingReprocessor

    @Autowired
    @Qualifier("cancellationReprocessor")
    private lateinit var cancellationReprocessor: RecordingReprocessor

    @BeforeEach
    fun setUp() {
        dlqMessageRepository.deleteAll()
        publisher.published.clear()
        sagaReprocessor.reset()
        cancellationReprocessor.reset()
    }

    @Test
    fun 처리기가_등록된_소비_그룹의_실패는_원래_토픽으로_재발행하지_않고_그_그룹의_처리기만_다시_부른다() {
        val id = saveFailure(kafkaService, CANCELLATION_GROUP)

        val result = kafkaService.retryDLQMessage(id)

        assertThat(result).isTrue()
        assertThat(publisher.published).isEmpty()
        assertThat(cancellationReprocessor.received).containsExactly(VALUE)
        assertThat(sagaReprocessor.received).isEmpty()
        val saved = dlqMessageRepository.findByIdOrNull(id)!!
        assertThat(saved.status).isEqualTo(DLQStatus.PROCESSED)
        assertThat(saved.retryCount).isEqualTo(1)
    }

    @Test
    fun 같은_레코드가_두_소비_그룹에서_실패하면_그룹마다_따로_남기고_각자의_처리기로_다시_처리한다() {
        val cancellationId = saveFailure(kafkaService, CANCELLATION_GROUP)
        val sagaId = saveFailure(kafkaService, SAGA_GROUP)

        assertThat(dlqMessageRepository.count()).isEqualTo(2)
        assertThat(kafkaService.retryDLQMessage(cancellationId)).isTrue()
        assertThat(kafkaService.retryDLQMessage(sagaId)).isTrue()

        assertThat(cancellationReprocessor.received).containsExactly(VALUE)
        assertThat(sagaReprocessor.received).containsExactly(VALUE)
        assertThat(publisher.published).isEmpty()
    }

    @Test
    fun 처리기가_실패하면_처리기의_변경만_되돌리고_재시도_횟수를_올려_다음_재시도로_미룬다() {
        val id = saveFailure(kafkaService, CANCELLATION_GROUP)
        cancellationReprocessor.failure = IllegalStateException("주문 상태 반영 실패")

        val result = kafkaService.retryDLQMessage(id)

        assertThat(result).isFalse()
        assertThat(dlqMessageRepository.count()).isEqualTo(1)
        val saved = dlqMessageRepository.findByIdOrNull(id)!!
        assertThat(saved.status).isEqualTo(DLQStatus.PENDING)
        assertThat(saved.retryCount).isEqualTo(1)
        assertThat(saved.nextRetryAt).isNotNull()
        assertThat(saved.exceptionMessage).contains("주문 상태 반영 실패")
        assertThat(publisher.published).isEmpty()
    }

    @Test
    fun 처리기가_비재시도_예외로_실패하면_FAILED로_바꾼다() {
        val id = saveFailure(kafkaService, CANCELLATION_GROUP)
        cancellationReprocessor.failure = IllegalArgumentException("알 수 없는 이벤트")

        val result = kafkaService.retryDLQMessage(id)

        assertThat(result).isFalse()
        val saved = dlqMessageRepository.findByIdOrNull(id)!!
        assertThat(saved.status).isEqualTo(DLQStatus.FAILED)
        assertThat(saved.retryCount).isEqualTo(1)
    }

    @Test
    fun 처리기가_없는_소비_그룹의_실패는_지금처럼_원래_토픽으로_재발행한다() {
        val id = saveFailure(kafkaService, "statistics-compensation-service")

        val result = kafkaService.retryDLQMessage(id)

        assertThat(result).isTrue()
        assertThat(publisher.published).containsExactly(TOPIC)
        assertThat(cancellationReprocessor.received).isEmpty()
        assertThat(sagaReprocessor.received).isEmpty()
    }

    @Test
    fun 소비_그룹이_없는_실패는_지금처럼_원래_토픽으로_재발행한다() {
        val id = saveFailure(kafkaService, null)

        val result = kafkaService.retryDLQMessage(id)

        assertThat(result).isTrue()
        assertThat(publisher.published).containsExactly(TOPIC)
        assertThat(cancellationReprocessor.received).isEmpty()
    }

    @Test
    fun Kafka_재발행기가_없어도_처리기가_있으면_FAILED로_저장하지_않고_직접_재처리한다() {
        val id = saveFailure(noKafkaService, CANCELLATION_GROUP)
        assertThat(dlqMessageRepository.findByIdOrNull(id)!!.status).isEqualTo(DLQStatus.PENDING)

        val result = noKafkaService.retryDLQMessage(id)

        assertThat(result).isTrue()
        assertThat(cancellationReprocessor.received).containsExactly(VALUE)
        assertThat(dlqMessageRepository.findByIdOrNull(id)!!.status).isEqualTo(DLQStatus.PROCESSED)
    }

    @Test
    fun 같은_토픽과_소비_그룹에_처리기를_둘_등록하면_만들_때_실패한다() {
        assertThatThrownBy {
            DLQReprocessors(
                listOf(
                    RecordingReprocessor(TOPIC, SAGA_GROUP),
                    RecordingReprocessor(TOPIC, SAGA_GROUP)
                )
            )
        }.isInstanceOf(IllegalStateException::class.java)
    }

    private fun saveFailure(service: DLQCommandService, consumerGroup: String?): Long {
        service.saveDLQMessage(
            DeadLetterMessage(
                originalTopic = TOPIC,
                originalPartition = 0,
                originalOffset = 7L,
                originalKey = "300",
                originalValue = VALUE,
                exception = "java.lang.IllegalStateException: 처리 실패",
                timestamp = System.currentTimeMillis(),
                consumerGroup = consumerGroup
            )
        )
        return dlqMessageRepository.findAll().maxOf { it.id!! }
    }

    class RecordingPublisher : DLQMessagePublisher {
        val published = CopyOnWriteArrayList<String>()

        override fun publish(topic: String, key: String, value: Any?): Boolean {
            published.add(topic)
            return true
        }
    }

    class RecordingReprocessor(
        override val topic: String,
        override val consumerGroup: String,
        private val repository: DLQMessageRepository? = null,
        private val transactionManager: PlatformTransactionManager? = null
    ) : DLQMessageReprocessor {
        val received = CopyOnWriteArrayList<String?>()
        var failure: RuntimeException? = null

        override fun reprocess(key: String?, value: String?) {
            val error = failure
            if (error == null) {
                received.add(value)
                return
            }
            TransactionTemplate(transactionManager!!).executeWithoutResult {
                repository!!.save(
                    DLQMessage(
                        originalTopic = "side-effect",
                        originalPartition = 0,
                        originalOffset = 0L,
                        originalKey = key,
                        originalValue = value,
                        exceptionMessage = null,
                        errorTimestamp = System.currentTimeMillis()
                    )
                )
                throw error
            }
        }

        fun reset() {
            received.clear()
            failure = null
        }
    }

    @Configuration
    @EntityScan(basePackageClasses = [DLQMessage::class])
    @EnableJpaRepositories(basePackageClasses = [DLQMessageRepository::class])
    class TestConfig {

        @Bean
        fun recordingPublisher(): RecordingPublisher = RecordingPublisher()

        @Bean
        fun sagaReprocessor(repository: DLQMessageRepository, transactionManager: PlatformTransactionManager) =
            RecordingReprocessor(TOPIC, SAGA_GROUP, repository, transactionManager)

        @Bean
        fun cancellationReprocessor(repository: DLQMessageRepository, transactionManager: PlatformTransactionManager) =
            RecordingReprocessor(TOPIC, CANCELLATION_GROUP, repository, transactionManager)

        @Bean
        fun dlqReprocessors(reprocessors: List<DLQMessageReprocessor>): DLQReprocessors = DLQReprocessors(reprocessors)

        @Bean
        fun kafkaService(
            repository: DLQMessageRepository,
            publisher: RecordingPublisher,
            reprocessors: DLQReprocessors
        ): DLQCommandService =
            DLQCommandService(repository, publisher, NoOpDLQMetrics(), NoOpDLQArchivalService(), null, reprocessors)

        @Bean
        fun noKafkaService(repository: DLQMessageRepository, reprocessors: DLQReprocessors): DLQCommandService =
            DLQCommandService(
                repository, NoOpDLQMessagePublisher(), NoOpDLQMetrics(), NoOpDLQArchivalService(), null, reprocessors
            )
    }

    companion object {
        private const val TOPIC = "payment-compensation"
        private const val SAGA_GROUP = "order-saga-service"
        private const val CANCELLATION_GROUP = "order-cancellation-result-service"
        private const val VALUE = """{"eventType":"PaymentCancelled","orderId":300}"""
    }
}
