package com.hoppingmall.order.order.service

import com.hoppingmall.order.order.domain.ReservationFenceEntry
import com.hoppingmall.order.support.MySqlJpaTestSupport
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.util.Collections
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

@DisplayName("ReservationFence MySQL 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@Import(ReservationFence::class)
@TestPropertySource(properties = ["order.reservation.fence.enabled=true"])
class ReservationFenceMySqlIntegrationTest : MySqlJpaTestSupport() {

    @Autowired
    private lateinit var fence: ReservationFence

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    @Autowired
    private lateinit var entityManager: EntityManager

    private lateinit var tx: TransactionTemplate

    @BeforeEach
    fun setUp() {
        tx = TransactionTemplate(transactionManager)
    }

    @Test
    fun 엔티티가_만든_fence_테이블과_주문_항목_인덱스가_있고_생성_시각은_기본값으로_채워진다() {
        val columns = jdbcTemplate.queryForList(
            """
            SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_KEY, COLUMN_DEFAULT FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'reservation_fence' ORDER BY ORDINAL_POSITION
            """.trimIndent()
        ).associateBy { it["COLUMN_NAME"] as String }
        val rid = newId()

        jdbcTemplate.update("INSERT INTO reservation_fence (reservation_id, decision) VALUES (?, 'LINKED')", rid)

        assertThat(columns.keys).containsExactlyInAnyOrder("reservation_id", "decision", "created_at")
        assertThat(columns.getValue("reservation_id")["COLUMN_TYPE"]).isEqualTo("varchar(36)")
        assertThat(columns.getValue("reservation_id")["COLUMN_KEY"]).isEqualTo("PRI")
        assertThat(columns.getValue("decision")["COLUMN_TYPE"]).isEqualTo("varchar(10)")
        assertThat(columns.getValue("decision")["IS_NULLABLE"]).isEqualTo("NO")
        assertThat(columns.getValue("created_at")["COLUMN_TYPE"]).isEqualTo("datetime(6)")
        assertThat(columns.getValue("created_at")["IS_NULLABLE"]).isEqualTo("NO")
        assertThat(columns.getValue("created_at")["COLUMN_DEFAULT"].toString()).startsWith("CURRENT_TIMESTAMP")
        assertThat(
            jdbcTemplate.queryForObject("SELECT created_at IS NOT NULL FROM reservation_fence WHERE reservation_id = ?", Boolean::class.java, rid)
        ).isTrue()
        assertThat(
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'order_items'
                AND INDEX_NAME = 'idx_order_items_reservation_id' AND COLUMN_NAME = 'reservation_id'
                """.trimIndent(),
                Int::class.java
            )
        ).isEqualTo(1)
    }

    @Test
    fun 엔티티로_저장한_결정을_fence가_읽고_fence가_쓴_결정을_엔티티로_읽는다() {
        val savedByEntity = newId()
        val writtenByFence = newId()

        tx.executeWithoutResult {
            entityManager.persist(ReservationFenceEntry(reservationId = savedByEntity, decision = ReservationFence.LINKED))
        }
        fence.decide(writtenByFence)
        val entry = tx.execute { entityManager.find(ReservationFenceEntry::class.java, writtenByFence) }!!

        assertThat(fence.decisionOf(savedByEntity)).isEqualTo(ReservationFence.LINKED)
        assertThat(entry.reservationId).isEqualTo(writtenByFence)
        assertThat(entry.decision).isEqualTo(ReservationFence.ABORTED)
        assertThat(entry.createdAt).isNotNull()
    }

    @Test
    fun 주문이_LINKED를_넣고_커밋하기_전에_온_조회는_기다렸다가_LINKED를_받는다() {
        val rid = newId()
        lateinit var decision: Future<String>

        val fenced = orderTx(listOf(rid)) {
            decision = decideWhileOrderHolds(rid)
        }

        assertThat(fenced).isEmpty()
        assertThat(decision.get(30, TimeUnit.SECONDS)).isEqualTo(ReservationFence.LINKED)
        assertThat(stored(rid)).containsExactly(ReservationFence.LINKED)
    }

    @Test
    fun 조회가_먼저_ABORTED를_쓰면_주문의_link는_그_예약을_충돌로_돌려준다() {
        val rid = newId()

        assertThat(fence.decide(rid)).isEqualTo(ReservationFence.ABORTED)
        val fenced = orderTx(listOf(rid))

        assertThat(fenced).containsExactly(rid)
        assertThat(stored(rid)).containsExactly(ReservationFence.ABORTED)
    }

    @Test
    fun 주문_트랜잭션이_롤백되면_기다리던_조회는_ABORTED를_쓴다() {
        val rid = newId()
        lateinit var decision: Future<String>

        val fenced = orderTx(listOf(rid), rollbackAfterLink = true) {
            decision = decideWhileOrderHolds(rid)
        }

        assertThat(fenced).isEmpty()
        assertThat(decision.get(30, TimeUnit.SECONDS)).isEqualTo(ReservationFence.ABORTED)
        assertThat(stored(rid)).containsExactly(ReservationFence.ABORTED)
    }

    @Test
    fun 주문과_조회가_동시에_시작해도_결정은_하나다() {
        var orderWon = 0
        var checkWon = 0

        repeat(RACE_REPEATS) {
            val rid = newId()
            val ready = CountDownLatch(2)
            val start = CountDownLatch(1)
            val order = executor.submit<List<String>> { ready.countDown(); start.await(); orderTx(listOf(rid)) }
            val check = executor.submit<String> { ready.countDown(); start.await(); fence.decide(rid) }
            ready.await()
            start.countDown()
            val fenced = order.get(30, TimeUnit.SECONDS)
            val decision = check.get(30, TimeUnit.SECONDS)

            if (fenced.isEmpty()) {
                orderWon++
                assertThat(decision).isEqualTo(ReservationFence.LINKED)
                assertThat(stored(rid)).containsExactly(ReservationFence.LINKED)
            } else {
                checkWon++
                assertThat(fenced).containsExactly(rid)
                assertThat(decision).isEqualTo(ReservationFence.ABORTED)
                assertThat(stored(rid)).containsExactly(ReservationFence.ABORTED)
            }
        }

        assertThat(orderWon + checkWon).isEqualTo(RACE_REPEATS)
        assertThat(rowCount()).isEqualTo(RACE_REPEATS)
    }

    @Test
    fun 같은_예약에_동시_조회_5개가_와도_결정은_한_행이고_모두_같은_값을_받는다() {
        val rid = newId()
        val start = CountDownLatch(1)
        val results = Collections.synchronizedList(mutableListOf<String>())
        val futures = (1..5).map { executor.submit { start.await(); results.add(fence.decide(rid)) } }

        start.countDown()
        futures.forEach { it.get(30, TimeUnit.SECONDS) }

        assertThat(results).hasSize(5).containsOnly(ReservationFence.ABORTED)
        assertThat(stored(rid)).containsExactly(ReservationFence.ABORTED)
    }

    @Test
    fun 예약_여러_개_중_하나만_ABORTED면_link는_그_예약만_돌려주고_롤백_뒤_나머지의_LINKED는_남지_않는다() {
        val first = newId()
        val aborted = newId()
        val last = newId()
        assertThat(fence.decide(aborted)).isEqualTo(ReservationFence.ABORTED)

        val fenced = orderTx(listOf(first, aborted, last))

        assertThat(fenced).containsExactly(aborted)
        assertThat(stored(first)).isEmpty()
        assertThat(stored(last)).isEmpty()
        assertThat(stored(aborted)).containsExactly(ReservationFence.ABORTED)
        assertThat(rowCount()).isEqualTo(1)
    }

    private fun decideWhileOrderHolds(rid: String): Future<String> {
        val enteredAt = AtomicLong(0L)
        val decision = executor.submit<String> {
            enteredAt.set(System.currentTimeMillis())
            fence.decide(rid)
        }
        awaitLockWait(decision, enteredAt, "연결 조회", "reservation_fence")
        return decision
    }

    private fun orderTx(reservationIds: List<String>, rollbackAfterLink: Boolean = false, afterLink: () -> Unit = {}): List<String> {
        var fenced = emptyList<String>()
        tx.executeWithoutResult { status ->
            fenced = fence.link(reservationIds)
            if (fenced.isNotEmpty()) {
                status.setRollbackOnly()
                return@executeWithoutResult
            }
            afterLink()
            if (rollbackAfterLink) status.setRollbackOnly()
        }
        return fenced
    }

    private fun stored(rid: String): List<String> =
        jdbcTemplate.queryForList("SELECT decision FROM reservation_fence WHERE reservation_id = ?", String::class.java, rid)

    private fun rowCount(): Int =
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM reservation_fence", Int::class.java)!!

    private fun newId(): String = UUID.randomUUID().toString()

    companion object {
        private const val RACE_REPEATS = 50
    }
}
