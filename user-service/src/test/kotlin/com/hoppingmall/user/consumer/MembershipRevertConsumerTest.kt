package com.hoppingmall.user.consumer

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.hoppingmall.common.KafkaTopics
import com.hoppingmall.common.event.AvroEventConverter
import com.hoppingmall.common.event.AvroJsonDeserializer
import com.hoppingmall.common.event.KafkaEventMapper
import com.hoppingmall.user.domain.Membership
import com.hoppingmall.user.domain.enums.MembershipGrade
import com.hoppingmall.user.domain.repository.MembershipEventLogRepository
import com.hoppingmall.user.domain.repository.MembershipRepository
import com.hoppingmall.user.service.MembershipCommandServiceImpl
import io.confluent.kafka.schemaregistry.testutil.MockSchemaRegistry
import io.confluent.kafka.serializers.KafkaAvroSerializer
import io.confluent.kafka.serializers.subject.TopicRecordNameStrategy
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@DisplayName("멤버십 되돌리기 처리")
@DisplayNameGeneration(ReplaceUnderscores::class)
@DataJpaTest
@Import(
    MembershipCommandServiceImpl::class,
    MembershipEventConsumer::class,
    MembershipRevertConsumerTest.TestConfig::class
)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class MembershipRevertConsumerTest {

    @TestConfiguration
    class TestConfig {
        @Bean
        fun kafkaEventMapper(): KafkaEventMapper =
            KafkaEventMapper(ObjectMapper().setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE))
    }

    @Autowired
    private lateinit var membershipEventConsumer: MembershipEventConsumer

    @Autowired
    private lateinit var membershipRepository: MembershipRepository

    @Autowired
    private lateinit var membershipEventLogRepository: MembershipEventLogRepository

    private val scope = "membership-revert-consumer"
    private val userId = 37L
    private val converter = AvroEventConverter(jacksonObjectMapper())

    @BeforeEach
    fun setUp() {
        membershipEventLogRepository.deleteAll()
        membershipRepository.deleteAll()
    }

    @AfterEach
    fun tearDown() {
        MockSchemaRegistry.dropScope(scope)
    }

    private fun send(eventType: String, eventData: Map<String, Any?>) {
        val config = mapOf(
            "schema.registry.url" to "mock://$scope",
            "value.subject.name.strategy" to TopicRecordNameStrategy::class.java.name
        )
        val serializer = KafkaAvroSerializer().apply { configure(config, false) }
        val deserializer = AvroJsonDeserializer().apply { configure(config, false) }
        val topic = KafkaTopics.MEMBERSHIP_UPDATE_REQUEST
        val record = converter.convertJsonToAvro(eventType, jacksonObjectMapper().writeValueAsString(eventData))
        val message = deserializer.deserialize(topic, serializer.serialize(topic, record))!!
        membershipEventConsumer.handleMembershipUpdateRequest(message)
    }

    private fun purchase(paymentId: Long, amount: String) = send(
        "MembershipUpdateRequested",
        mapOf(
            "eventId" to "membership-TXN_$paymentId",
            "userId" to userId,
            "orderId" to paymentId,
            "paymentId" to paymentId,
            "amount" to amount
        )
    )

    private fun cancel(paymentId: Long, amount: String) = send(
        "MembershipRevertRequested",
        mapOf(
            "eventId" to "membership-revert-cancel-$paymentId",
            "userId" to userId,
            "orderId" to paymentId,
            "paymentId" to paymentId,
            "amount" to amount,
            "reason" to "CANCEL",
            "refundId" to null
        )
    )

    private fun refund(paymentId: Long, refundId: Long, amount: String) = send(
        "MembershipRevertRequested",
        mapOf(
            "eventId" to "membership-revert-refund-$refundId",
            "userId" to userId,
            "orderId" to paymentId,
            "paymentId" to paymentId,
            "amount" to amount,
            "reason" to "REFUND",
            "refundId" to refundId
        )
    )

    private fun membership(): Membership = membershipRepository.findByUserId(userId)!!

    @Test
    fun 결제_뒤_취소되면_누적액과_등급이_결제_전_값으로_돌아간다() {
        purchase(paymentId = 1L, amount = "50000")
        assertThat(membership().totalSpent).isEqualByComparingTo("50000")
        assertThat(membership().grade).isEqualTo(MembershipGrade.BRONZE)

        purchase(paymentId = 2L, amount = "60000")
        assertThat(membership().grade).isEqualTo(MembershipGrade.SILVER)

        cancel(paymentId = 2L, amount = "60000")

        assertThat(membership().totalSpent).isEqualByComparingTo("50000")
        assertThat(membership().grade).isEqualTo(MembershipGrade.BRONZE)
    }

    @Test
    fun 부분_환불은_그_금액만큼만_깎는다() {
        purchase(paymentId = 1L, amount = "150000")

        refund(paymentId = 1L, refundId = 11L, amount = "30000")

        assertThat(membership().totalSpent).isEqualByComparingTo("120000")
        assertThat(membership().grade).isEqualTo(MembershipGrade.SILVER)

        refund(paymentId = 1L, refundId = 12L, amount = "60000")

        assertThat(membership().totalSpent).isEqualByComparingTo("60000")
        assertThat(membership().grade).isEqualTo(MembershipGrade.BRONZE)
    }

    @Test
    fun 같은_되돌리기를_두_번_받아도_한_번만_깎는다() {
        purchase(paymentId = 1L, amount = "150000")

        refund(paymentId = 1L, refundId = 11L, amount = "30000")
        refund(paymentId = 1L, refundId = 11L, amount = "30000")

        assertThat(membership().totalSpent).isEqualByComparingTo("120000")
        assertThat(membershipEventLogRepository.count()).isEqualTo(2L)
    }

    @Test
    fun 누적액보다_큰_되돌리기는_0에서_멈춘다() {
        purchase(paymentId = 1L, amount = "10000")

        cancel(paymentId = 1L, amount = "50000")

        assertThat(membership().totalSpent).isEqualByComparingTo("0")
        assertThat(membership().grade).isEqualTo(MembershipGrade.BRONZE)
    }

    @Test
    fun 더하기보다_먼저_온_되돌리기는_완료로_남지_않고_예외로_끝나_다시_시도된다() {
        val thrown = catchThrowable { cancel(paymentId = 1L, amount = "60000") }

        assertThat(thrown).isNotNull()
        assertThat(membershipEventLogRepository.count()).isZero()
        assertThat(membershipRepository.findByUserId(userId)?.totalSpent ?: BigDecimal.ZERO).isEqualByComparingTo("0")

        purchase(paymentId = 1L, amount = "60000")
        cancel(paymentId = 1L, amount = "60000")

        assertThat(membership().totalSpent).isEqualByComparingTo("0")
        assertThat(membershipEventLogRepository.count()).isEqualTo(2L)
    }
}
