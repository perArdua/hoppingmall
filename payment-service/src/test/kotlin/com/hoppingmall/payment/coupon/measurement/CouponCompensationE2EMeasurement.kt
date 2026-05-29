package com.hoppingmall.payment.coupon.measurement

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.hoppingmall.common.KafkaTopics
import com.hoppingmall.payment.coupon.dto.event.CouponRestoreEvent
import com.hoppingmall.payment.coupon.enum.CouponRestoreReason
import com.hoppingmall.payment.coupon.infrastructure.CouponRestoreResult
import com.hoppingmall.payment.coupon.infrastructure.CouponStockRedisRepository
import com.hoppingmall.payment.coupon.metrics.CouponCompensationMetrics
import com.hoppingmall.payment.coupon.service.CouponCompensationProcessor
import com.hoppingmall.payment.coupon.service.CouponCompensationPublisher
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.redisson.Redisson
import org.redisson.api.RedissonClient
import org.redisson.config.Config
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import java.time.Duration
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue

@Tag("e2e")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CouponCompensationE2EMeasurement {

    private val bootstrapServers = "localhost:9092"
    private val testCouponIdBase = 9000L

    private lateinit var redisson: RedissonClient
    private lateinit var redisRepo: CouponStockRedisRepository
    private lateinit var publisher: CouponCompensationPublisher
    private lateinit var processor: CouponCompensationProcessor
    private lateinit var metrics: CouponCompensationMetrics
    private lateinit var meterRegistry: SimpleMeterRegistry
    private val objectMapper = ObjectMapper()
        .registerModule(KotlinModule.Builder().build())
        .registerModule(JavaTimeModule())

    @BeforeAll
    fun setUp() {
        val redissonConfig = Config()
        redissonConfig.useSingleServer().apply {
            address = "redis://127.0.0.1:6390"
            connectionPoolSize = 32
        }
        redisson = Redisson.create(redissonConfig)
        redisRepo = CouponStockRedisRepository(redisson)

        meterRegistry = SimpleMeterRegistry()
        metrics = CouponCompensationMetrics(meterRegistry)

        val producerProps = HashMap<String, Any>()
        producerProps[ProducerConfig.BOOTSTRAP_SERVERS_CONFIG] = bootstrapServers
        producerProps[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java
        producerProps[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java
        producerProps[ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG] = true
        producerProps[ProducerConfig.ACKS_CONFIG] = "all"
        producerProps[ProducerConfig.RETRIES_CONFIG] = 5
        producerProps[ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION] = 5

        val producerFactory = DefaultKafkaProducerFactory<String, String>(producerProps)
        val kafkaTemplate = KafkaTemplate(producerFactory)
        publisher = CouponCompensationPublisher(kafkaTemplate, objectMapper, metrics)
        processor = CouponCompensationProcessor(redisRepo, metrics)
    }

    @AfterAll
    fun tearDown() {
        if (::redisson.isInitialized) redisson.shutdown()
    }

    private fun newConsumer(groupId: String, topic: String): KafkaConsumer<String, String> {
        val props = HashMap<String, Any>()
        props[ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG] = bootstrapServers
        props[ConsumerConfig.GROUP_ID_CONFIG] = groupId
        props[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "earliest"
        props[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
        props[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
        props[ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG] = false
        val consumer = KafkaConsumer<String, String>(props)
        consumer.subscribe(listOf(topic))
        return consumer
    }

    private fun cleanCouponState(couponId: Long, totalQuantity: Int) {
        redisson.keys.deleteByPattern("coupon:{$couponId}:*")
        redisRepo.initializeStock(couponId, totalQuantity)
    }

    private fun preDecrementStock(couponId: Long, userIds: List<Long>) {
        userIds.forEach { redisRepo.tryReserve(couponId, it) }
    }

    @Test
    fun scenario1_정상_보상_흐름의_처리량과_지연을_측정한다() {
        val couponId = testCouponIdBase + 1
        val totalQuantity = 100
        val numEvents = 100
        val userIds = (1L..numEvents.toLong()).toList()
        cleanCouponState(couponId, totalQuantity)
        preDecrementStock(couponId, userIds)

        val consumer = newConsumer("e2e-measure-${UUID.randomUUID()}", KafkaTopics.COUPON_RESTORE)
        try {
            val publishStart = System.currentTimeMillis()
            val publishTimestamps = HashMap<Long, Long>()
            userIds.forEach { uid ->
                publishTimestamps[uid] = System.currentTimeMillis()
                publisher.publish(CouponRestoreEvent(couponId, uid, CouponRestoreReason.DB_INSERT_FAILED))
            }
            val publishElapsed = System.currentTimeMillis() - publishStart

            val lags = mutableListOf<Long>()
            val processed = HashSet<Long>()
            val deadline = System.currentTimeMillis() + 30_000L
            while (processed.size < numEvents && System.currentTimeMillis() < deadline) {
                val records = consumer.poll(Duration.ofMillis(500))
                for (rec in records) {
                    val event = objectMapper.readValue(rec.value(), CouponRestoreEvent::class.java)
                    if (event.couponId != couponId) continue
                    if (!processed.add(event.userId)) continue
                    val consumeTs = System.currentTimeMillis()
                    processor.process(event)
                    publishTimestamps[event.userId]?.let { publishTs ->
                        lags.add(consumeTs - publishTs)
                    }
                }
            }

            val sorted = lags.sorted()
            val p50 = if (sorted.isNotEmpty()) sorted[sorted.size / 2] else -1
            val p95 = if (sorted.isNotEmpty()) sorted[(sorted.size * 95 / 100).coerceAtMost(sorted.size - 1)] else -1
            val p99 = if (sorted.isNotEmpty()) sorted[(sorted.size * 99 / 100).coerceAtMost(sorted.size - 1)] else -1
            val maxLag = sorted.maxOrNull() ?: -1
            val publishThroughput = numEvents * 1000.0 / publishElapsed.coerceAtLeast(1)

            val finalStock = (redisson.getBucket<String>("coupon:{$couponId}:stock", org.redisson.client.codec.StringCodec.INSTANCE).get())?.toLongOrNull() ?: -1
            val finalIssuedSet = redisRepo.getIssuedUserIds(couponId).size

            println("\n=== Scenario 1: 정상 보상 흐름 ===")
            println("  처리 이벤트: $numEvents")
            println("  발행 처리량: %.0f events/sec".format(publishThroughput))
            println("  처리 지연 (publish -> consume+restore):")
            println("    P50:  ${p50}ms")
            println("    P95:  ${p95}ms")
            println("    P99:  ${p99}ms")
            println("    Max:  ${maxLag}ms")
            println("  최종 정합성:")
            println("    Redis 잔여 = $finalStock (발행 전 0, 복원 후 기대값 $numEvents)")
            println("    issued Set 크기 = $finalIssuedSet (복원 후 기대값 0)")
            println("  메트릭:")
            println("    async.published = ${meterRegistry.counter("coupon.compensation.async.published").count().toLong()}")
            println("    async.consumed  = ${meterRegistry.counter("coupon.compensation.async.consumed").count().toLong()}")

            assertThat(processed.size).isEqualTo(numEvents)
            assertThat(finalStock).isEqualTo(numEvents.toLong())
            assertThat(finalIssuedSet).isZero()
        } finally {
            consumer.close()
        }
    }

    @Test
    fun scenario2_같은_이벤트_5회_중복_수신_시_Redis_잔여는_단_1번만_복원된다() {
        val couponId = testCouponIdBase + 2
        val userId = 42L
        cleanCouponState(couponId, 100)
        redisRepo.tryReserve(couponId, userId)

        val initialStock = (redisson.getBucket<String>("coupon:{$couponId}:stock", org.redisson.client.codec.StringCodec.INSTANCE).get())?.toLongOrNull() ?: -1
        val results = mutableListOf<CouponRestoreResult>()
        repeat(5) {
            results.add(redisRepo.restoreStockIdempotent(couponId, userId))
        }
        val finalStock = (redisson.getBucket<String>("coupon:{$couponId}:stock", org.redisson.client.codec.StringCodec.INSTANCE).get())?.toLongOrNull() ?: -1

        println("\n=== Scenario 2: 멱등성 (같은 이벤트 5회 중복) ===")
        println("  reserve 후 잔여: $initialStock (기대 99)")
        println("  5회 호출 결과: ${results.map { it::class.simpleName }}")
        println("  최종 잔여: $finalStock (기대 100 - 단 1회만 복원)")

        assertThat(initialStock).isEqualTo(99)
        assertThat(finalStock).isEqualTo(100)
        assertThat(results.first()).isInstanceOf(CouponRestoreResult.Restored::class.java)
        assertThat(results.drop(1)).allMatch { it is CouponRestoreResult.AlreadyRestored }
    }

    @Test
    fun scenario3_DLQ_재발행_횟수와_DLQ_토픽_도착을_확인한다() {
        val couponId = testCouponIdBase + 3
        val userId = 7L
        val dlqMessages = ConcurrentLinkedQueue<String>()
        cleanCouponState(couponId, 100)

        val dlqConsumer = newConsumer("e2e-dlq-${UUID.randomUUID()}", KafkaTopics.COUPON_RESTORE_DLQ)
        try {
            val event = CouponRestoreEvent(
                couponId = couponId,
                userId = userId,
                reason = CouponRestoreReason.COMPENSATION_FAILED,
                retryCount = 3
            )
            publisher.publishToDlq(event, "simulated repeated failure")

            val deadline = System.currentTimeMillis() + 10_000L
            while (dlqMessages.isEmpty() && System.currentTimeMillis() < deadline) {
                val records = dlqConsumer.poll(Duration.ofMillis(500))
                for (rec in records) {
                    if (rec.value().contains("\"couponId\":$couponId")) {
                        dlqMessages.add(rec.value())
                    }
                }
            }

            println("\n=== Scenario 3: DLQ 발행 ===")
            println("  DLQ 토픽 수신 메시지 수: ${dlqMessages.size}")
            println("  DLQ 메트릭: ${meterRegistry.counter("coupon.compensation.dlq").count().toLong()}")
            val payload = dlqMessages.firstOrNull()
            if (payload != null) {
                println("  DLQ payload: ${payload.take(200)}")
                assertThat(payload).contains("\"failureReason\":\"simulated repeated failure\"")
                assertThat(payload).contains("\"retryCount\":3")
                assertThat(payload).contains("\"originalEvent\"")
            }
            assertThat(dlqMessages.size).isGreaterThanOrEqualTo(1)
        } finally {
            dlqConsumer.close()
        }
    }
}
