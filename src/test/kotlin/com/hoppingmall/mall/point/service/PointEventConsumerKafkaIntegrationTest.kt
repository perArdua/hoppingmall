package com.hoppingmall.mall.point.service

import com.hoppingmall.mall.payment.dto.event.PointEarnRequestEvent
import com.hoppingmall.mall.point.domain.PointRepository
import com.hoppingmall.mall.point.domain.PointHistoryRepository
import org.junit.jupiter.api.*
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import java.math.BigDecimal
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals

@SpringBootTest
@ActiveProfiles("test")
@EmbeddedKafka(partitions = 4, topics = ["point-earn-request", "notification"])
@DisplayName("PointEventConsumer Kafka 통합 테스트")
@DisplayNameGeneration(ReplaceUnderscores::class)
class PointEventConsumerKafkaIntegrationTest {

    @Autowired
    private lateinit var kafkaTemplate: KafkaTemplate<String, Any>

    @Autowired
    private lateinit var pointRepository: PointRepository

    @Autowired
    private lateinit var pointHistoryRepository: PointHistoryRepository

    @BeforeEach
    fun setUp() {
        pointHistoryRepository.deleteAll()
        pointRepository.deleteAll()
    }

    @Test
    @DisplayName("Kafka 파티셔닝 + Consumer 동시성 환경에서 동시성 문제 없이 포인트 적립이 처리된다")
    fun kafka_동시성_통합_테스트() {
        // Given
        val userId = 1L
        val earnAmount = BigDecimal("100")
        val threadCount = 8
        val executor = Executors.newFixedThreadPool(threadCount)
        val barrier = CyclicBarrier(threadCount)
        val latch = CountDownLatch(threadCount)
        val successCount = AtomicInteger(0)
        val failureCount = AtomicInteger(0)

        println("=== Kafka 통합 테스트 시작 ===")
        Thread.sleep(1000) // Consumer 준비 대기

        // When - 여러 스레드가 동시에 Kafka로 메시지 전송
        repeat(threadCount) { threadIndex ->
            executor.submit {
                try {
                    barrier.await(3, TimeUnit.SECONDS)
                    val event = PointEarnRequestEvent(
                        userId = userId,
                        orderId = threadIndex.toLong(),
                        paymentId = threadIndex.toLong(),
                        earnAmount = earnAmount,
                        reason = "Kafka 통합 동시성 테스트"
                    )
                    kafkaTemplate.send("point-earn-request", userId.toString(), event)
                    successCount.incrementAndGet()
                } catch (e: Exception) {
                    println("Thread $threadIndex failed: ${e.message}")
                    failureCount.incrementAndGet()
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await()
        executor.shutdown()

        // Consumer가 메시지를 모두 처리할 때까지 대기
        Thread.sleep(4000)

        // Then
        val points = pointRepository.findAll()
        val pointHistories = pointHistoryRepository.findAll()

        println("=== Kafka 통합 테스트 결과 ===")
        println("성공: ${successCount.get()}")
        println("실패: ${failureCount.get()}")
        println("생성된 Point 엔티티 수: ${points.size}")
        println("생성된 PointHistory 엔티티 수: ${pointHistories.size}")

        // Point 엔티티는 1개만 생성되어야 함
        assertEquals(1, points.size, "Point 엔티티가 중복 생성되었습니다.")
        // PointHistory는 모든 요청만큼 생성되어야 함
        assertEquals(threadCount, pointHistories.size, "PointHistory 엔티티 수가 예상과 다릅니다")
        // 포인트 잔액이 정확히 계산되어야 함
        val expectedBalance = earnAmount.multiply(BigDecimal(threadCount))
        val actualBalance = points.first().balance
        println("실제 잔액 $actualBalance")
        assertEquals(0, expectedBalance.compareTo(actualBalance), "포인트 잔액이 정확하지 않습니다. 예상: $expectedBalance, 실제: $actualBalance")
    }
}