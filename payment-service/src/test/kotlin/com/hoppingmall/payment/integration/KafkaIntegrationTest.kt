package com.hoppingmall.payment.integration

import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.testcontainers.containers.KafkaContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.time.Duration
import java.util.Properties

@Testcontainers
@DisplayName("Kafka E2E 통합 테스트")
@DisplayNameGeneration(ReplaceUnderscores::class)
class KafkaIntegrationTest {

    companion object {
        @Container
        val kafka = KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"))
    }

    private fun createProducer(): KafkaProducer<String, String> {
        val props = Properties().apply {
            put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.bootstrapServers)
            put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java)
            put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java)
            put(ProducerConfig.ACKS_CONFIG, "all")
        }
        return KafkaProducer(props)
    }

    private fun createConsumer(groupId: String): KafkaConsumer<String, String> {
        val props = Properties().apply {
            put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.bootstrapServers)
            put(ConsumerConfig.GROUP_ID_CONFIG, groupId)
            put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java)
            put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java)
            put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
        }
        return KafkaConsumer(props)
    }

    @Test
    fun 결제_완료_이벤트가_Kafka를_통해_정상_전달된다() {
        val topic = "payment-completed"
        val eventJson = """{"paymentId":1,"orderId":100,"userId":10,"amount":"50000","transactionId":"TXN_001","completedAt":"2026-03-22T12:00:00"}"""

        val producer = createProducer()
        producer.send(ProducerRecord(topic, "payment-1", eventJson)).get()
        producer.close()

        val consumer = createConsumer("test-payment-consumer")
        consumer.subscribe(listOf(topic))

        val records: ConsumerRecords<String, String> = consumer.poll(Duration.ofSeconds(10))
        consumer.close()

        assertThat(records.count()).isEqualTo(1)
        val record = records.first()
        assertThat(record.key()).isEqualTo("payment-1")
        assertThat(record.value()).contains("TXN_001")
        assertThat(record.value()).contains("50000")
    }

    @Test
    fun 포인트_적립_이벤트가_Kafka를_통해_정상_전달된다() {
        val topic = "point-earn-request"
        val eventJson = """{"eventId":"point-TXN_001","userId":10,"orderId":100,"paymentId":1,"earnAmount":"500","reason":"결제 완료"}"""

        val producer = createProducer()
        producer.send(ProducerRecord(topic, "point-1", eventJson)).get()
        producer.close()

        val consumer = createConsumer("test-point-consumer")
        consumer.subscribe(listOf(topic))

        val records = consumer.poll(Duration.ofSeconds(10))
        consumer.close()

        assertThat(records.count()).isEqualTo(1)
        val record = records.first()
        assertThat(record.value()).contains("point-TXN_001")
        assertThat(record.value()).contains("500")
    }

    @Test
    fun 결제_취소_이벤트가_Kafka를_통해_정상_전달된다() {
        val topic = "payment-cancelled"
        val eventJson = """{"eventId":"cancel-001","paymentId":1,"orderId":100,"userId":10,"amount":"50000","transactionId":"TXN_001"}"""

        val producer = createProducer()
        producer.send(ProducerRecord(topic, "cancel-1", eventJson)).get()
        producer.close()

        val consumer = createConsumer("test-cancel-consumer")
        consumer.subscribe(listOf(topic))

        val records = consumer.poll(Duration.ofSeconds(10))
        consumer.close()

        assertThat(records.count()).isEqualTo(1)
        assertThat(records.first().value()).contains("cancel-001")
    }

    @Test
    fun 여러_토픽에_동시에_이벤트를_발행하고_각각_소비할_수_있다() {
        val paymentTopic = "payment-multi-test"
        val pointTopic = "point-multi-test"
        val notificationTopic = "notification-multi-test"

        val producer = createProducer()
        producer.send(ProducerRecord(paymentTopic, "p1", """{"type":"payment"}""")).get()
        producer.send(ProducerRecord(pointTopic, "p2", """{"type":"point"}""")).get()
        producer.send(ProducerRecord(notificationTopic, "p3", """{"type":"notification"}""")).get()
        producer.close()

        val paymentConsumer = createConsumer("multi-payment")
        paymentConsumer.subscribe(listOf(paymentTopic))
        val paymentRecords = paymentConsumer.poll(Duration.ofSeconds(10))
        paymentConsumer.close()

        val pointConsumer = createConsumer("multi-point")
        pointConsumer.subscribe(listOf(pointTopic))
        val pointRecords = pointConsumer.poll(Duration.ofSeconds(10))
        pointConsumer.close()

        val notificationConsumer = createConsumer("multi-notification")
        notificationConsumer.subscribe(listOf(notificationTopic))
        val notificationRecords = notificationConsumer.poll(Duration.ofSeconds(10))
        notificationConsumer.close()

        assertThat(paymentRecords.count()).isEqualTo(1)
        assertThat(pointRecords.count()).isEqualTo(1)
        assertThat(notificationRecords.count()).isEqualTo(1)
        assertThat(paymentRecords.first().value()).contains("payment")
        assertThat(pointRecords.first().value()).contains("point")
        assertThat(notificationRecords.first().value()).contains("notification")
    }

    @Test
    fun Consumer_그룹_내에서_메시지가_한번만_소비된다() {
        val topic = "idempotency-test"
        val groupId = "idempotency-group"

        val producer = createProducer()
        producer.send(ProducerRecord(topic, "key1", """{"eventId":"evt-001"}""")).get()
        producer.close()

        val consumer1 = createConsumer(groupId)
        consumer1.subscribe(listOf(topic))
        val records1 = consumer1.poll(Duration.ofSeconds(10))
        consumer1.commitSync()
        consumer1.close()

        val consumer2 = createConsumer(groupId)
        consumer2.subscribe(listOf(topic))
        val records2 = consumer2.poll(Duration.ofSeconds(5))
        consumer2.close()

        assertThat(records1.count()).isEqualTo(1)
        assertThat(records2.count()).isEqualTo(0)
    }
}
