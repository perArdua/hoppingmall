package com.hoppingmall.user.consumer

import com.hoppingmall.common.KafkaTopics
import com.hoppingmall.dlq.domain.DLQStatus
import com.hoppingmall.dlq.domain.repository.DLQMessageRepository
import com.hoppingmall.dlq.publisher.DLQMessagePublisher
import com.hoppingmall.dlq.service.DLQCommandService
import com.hoppingmall.user.UserServiceApplicationTests
import com.hoppingmall.user.config.KafkaConsumerConfig
import com.hoppingmall.user.domain.repository.MembershipEventLogRepository
import com.hoppingmall.user.domain.repository.MembershipRepository
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.kafka.support.KafkaUtils
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.math.BigDecimal

@SpringBootTest
@Import(UserServiceApplicationTests.TestInfraConfig::class)
@TestPropertySource(properties = [
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration,org.redisson.spring.starter.RedissonAutoConfigurationV2,org.redisson.spring.starter.RedissonAutoConfigurationV4,org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration",
    "spring.main.allow-bean-definition-overriding=true",
    "spring.profiles.active=test"
])
@DisplayName("멤버십 컨슈머 DLQ 메시지의 소비 그룹별 직접 재처리")
@DisplayNameGeneration(ReplaceUnderscores::class)
class MembershipDLQDirectReprocessTest {

    @MockitoBean
    private lateinit var dlqMessagePublisher: DLQMessagePublisher

    @Autowired
    private lateinit var dlqCommandService: DLQCommandService

    @Autowired
    private lateinit var dlqMessageRepository: DLQMessageRepository

    @Autowired
    private lateinit var membershipRepository: MembershipRepository

    @Autowired
    private lateinit var membershipEventLogRepository: MembershipEventLogRepository

    @Autowired
    private lateinit var membershipEventConsumer: MembershipEventConsumer

    @BeforeEach
    fun setUp() {
        dlqMessageRepository.deleteAllInBatch()
        membershipEventLogRepository.deleteAllInBatch()
        membershipRepository.deleteAllInBatch()
    }

    @Test
    fun 멤버십_컨슈머가_재시도를_다_쓴_메시지는_소비_그룹과_함께_DLQ에_저장된다() {
        recoverInListenerThread(record(purchase("evt-membership-dlq-group")))

        val saved = dlqMessageRepository.findAll().single()
        assertThat(saved.originalTopic).isEqualTo(KafkaTopics.MEMBERSHIP_UPDATE_REQUEST)
        assertThat(saved.consumerGroup).isEqualTo(MEMBERSHIP_GROUP)
        assertThat(saved.status).isEqualTo(DLQStatus.PENDING)
    }

    @Test
    fun DLQ_재처리는_원래_토픽에_다시_발행하지_않고_누적_구매액을_직접_더한다() {
        recoverInListenerThread(record(purchase("evt-membership-dlq-direct")))
        val dlqMessageId = dlqMessageRepository.findAll().single().id!!

        val reprocessed = dlqCommandService.retryDLQMessage(dlqMessageId)

        assertThat(reprocessed).isTrue()
        verify(dlqMessagePublisher, never()).publish(any(), any(), anyOrNull())
        assertThat(totalSpent()).isEqualByComparingTo(PURCHASE_AMOUNT)
        assertThat(membershipEventLogRepository.existsByEventId("evt-membership-dlq-direct")).isTrue()
        val after = dlqMessageRepository.findById(dlqMessageId).orElseThrow()
        assertThat(after.status).isEqualTo(DLQStatus.PROCESSED)
        assertThat(after.retryCount).isEqualTo(1)
    }

    @Test
    fun 이미_처리된_구매의_DLQ_재처리는_누적_구매액을_한_번_더_더하지_않고_처리_완료로_끝난다() {
        val message = purchase("evt-membership-dlq-duplicate")
        membershipEventConsumer.handleMembershipUpdateRequest(message)
        recoverInListenerThread(record(message))
        val dlqMessageId = dlqMessageRepository.findAll().single().id!!

        val reprocessed = dlqCommandService.retryDLQMessage(dlqMessageId)

        assertThat(reprocessed).isTrue()
        verify(dlqMessagePublisher, never()).publish(any(), any(), anyOrNull())
        assertThat(totalSpent()).isEqualByComparingTo(PURCHASE_AMOUNT)
        assertThat(dlqMessageRepository.findById(dlqMessageId).orElseThrow().status).isEqualTo(DLQStatus.PROCESSED)
    }

    @Test
    fun 구매보다_먼저_DLQ에_간_되돌림은_재처리를_다시_기다렸다가_구매_처리_뒤_재처리에서_깎인다() {
        recoverInListenerThread(record(revert("evt-membership-dlq-revert")))
        val dlqMessageId = dlqMessageRepository.findAll().single().id!!

        val early = dlqCommandService.retryDLQMessage(dlqMessageId)

        assertThat(early).isFalse()
        assertThat(dlqMessageRepository.findById(dlqMessageId).orElseThrow().status).isEqualTo(DLQStatus.PENDING)

        membershipEventConsumer.handleMembershipUpdateRequest(purchase("evt-membership-dlq-purchase"))
        val late = dlqCommandService.retryDLQMessage(dlqMessageId)

        assertThat(late).isTrue()
        verify(dlqMessagePublisher, never()).publish(any(), any(), anyOrNull())
        assertThat(totalSpent()).isEqualByComparingTo(PURCHASE_AMOUNT.subtract(REVERT_AMOUNT))
        val after = dlqMessageRepository.findById(dlqMessageId).orElseThrow()
        assertThat(after.status).isEqualTo(DLQStatus.PROCESSED)
        assertThat(after.retryCount).isEqualTo(2)
    }

    private fun totalSpent(): BigDecimal =
        membershipRepository.findByUserId(USER_ID)!!.totalSpent

    private fun recoverInListenerThread(record: ConsumerRecord<String, Any>) {
        val recoverer = KafkaConsumerConfig(
            "localhost:9092", MEMBERSHIP_GROUP, "http://localhost:8081", dlqCommandService
        ).deadLetterRecoverer()
        KafkaUtils.setConsumerGroupId(MEMBERSHIP_GROUP)
        try {
            recoverer.accept(record, RuntimeException("멤버십 저장 일시 장애"))
        } finally {
            KafkaUtils.clearConsumerGroupId()
        }
    }

    private fun record(message: String): ConsumerRecord<String, Any> =
        ConsumerRecord(KafkaTopics.MEMBERSHIP_UPDATE_REQUEST, 0, 42L, USER_ID.toString(), message)

    private fun purchase(eventId: String): String =
        """{"eventId":"$eventId","userId":$USER_ID,"orderId":$ORDER_ID,"paymentId":$PAYMENT_ID,"amount":$PURCHASE_AMOUNT}"""

    private fun revert(eventId: String): String =
        """{"eventType":"MembershipRevertRequested","eventId":"$eventId","userId":$USER_ID,"orderId":$ORDER_ID,"paymentId":$PAYMENT_ID,"amount":$REVERT_AMOUNT,"reason":"REFUND","refundId":5}"""

    companion object {
        private const val MEMBERSHIP_GROUP = "membership-service"
        private const val USER_ID = 41L
        private const val ORDER_ID = 501L
        private const val PAYMENT_ID = 701L
        private val PURCHASE_AMOUNT = BigDecimal("30000")
        private val REVERT_AMOUNT = BigDecimal("12000")
    }
}
