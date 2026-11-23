package com.hoppingmall.order.order.service

import com.hoppingmall.order.order.domain.ReservationFenceEntry
import com.hoppingmall.order.support.MySqlContainerSupport
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
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.SingleConnectionDataSource
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

    @Test
    fun 취소_결정은_주문의_LINKED가_커밋되기_전이면_한도까지만_기다리고_취소_대상으로_돌려주지_않는다() {
        val rid = newId()
        lateinit var cancellable: List<String>
        var waitedMs = 0L

        val fenced = orderTx(listOf(rid)) {
            val startedAt = System.currentTimeMillis()
            cancellable = executor.submit<List<String>> { fence.abortForCancel(listOf(rid)) }.get(30, TimeUnit.SECONDS)
            waitedMs = System.currentTimeMillis() - startedAt
        }

        assertThat(fenced).isEmpty()
        assertThat(cancellable).isEmpty()
        assertThat(waitedMs).isGreaterThanOrEqualTo(CANCEL_LOCK_WAIT_MS - LOCK_WAIT_SLACK_MS).isLessThan(SERVER_LOCK_WAIT_MS)
        assertThat(stored(rid)).containsExactly(ReservationFence.LINKED)
    }

    @Test
    fun 주문의_LINKED가_먼저_커밋되면_취소_결정은_그_예약을_취소_대상으로_돌려주지_않는다() {
        val rid = newId()
        orderTx(listOf(rid))

        val cancellable = fence.abortForCancel(listOf(rid))

        assertThat(cancellable).isEmpty()
        assertThat(stored(rid)).containsExactly(ReservationFence.LINKED)
    }

    @Test
    fun 취소_결정이_먼저_커밋되면_그_예약을_취소_대상으로_돌려주고_주문의_link는_충돌로_돌려준다() {
        val rid = newId()

        val cancellable = fence.abortForCancel(listOf(rid))
        val fenced = orderTx(listOf(rid))

        assertThat(cancellable).containsExactly(rid)
        assertThat(fenced).containsExactly(rid)
        assertThat(stored(rid)).containsExactly(ReservationFence.ABORTED)
    }

    @Test
    fun 이미_ABORTED인_예약은_다시_취소_대상으로_돌려주고_행은_하나다() {
        val rid = newId()
        fence.decide(rid)

        val cancellable = fence.abortForCancel(listOf(rid, rid))

        assertThat(cancellable).containsExactly(rid)
        assertThat(rowCount()).isEqualTo(1)
    }

    @Test
    fun 취소_결정이_줄인_세션_잠금_한도는_기다림이_끝나면_원래대로_돌아온다() {
        val rid = newId()
        val single = SingleConnectionDataSource(MySqlContainerSupport.jdbcUrl(DATABASE), MySqlContainerSupport.username, MySqlContainerSupport.password, true)
        try {
            val singleJdbc = JdbcTemplate(single)
            val singleFence = ReservationFence(singleJdbc, false, CANCEL_LOCK_WAIT_SECONDS)
            val before = sessionLockWait(singleJdbc)
            lateinit var cancellable: List<String>

            orderTx(listOf(rid)) {
                cancellable = executor.submit<List<String>> { singleFence.abortForCancel(listOf(rid)) }.get(30, TimeUnit.SECONDS)
            }

            assertThat(cancellable).isEmpty()
            assertThat(before).isEqualTo(SERVER_LOCK_WAIT_MS / 1000)
            assertThat(sessionLockWait(singleJdbc)).isEqualTo(before)
        } finally {
            single.destroy()
        }
    }

    private fun sessionLockWait(jdbc: JdbcTemplate): Long =
        jdbc.queryForObject("SELECT @@SESSION.innodb_lock_wait_timeout", Long::class.java)!!

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
        private const val DATABASE = "order_mysql_test"
        private const val CANCEL_LOCK_WAIT_SECONDS = 1
        private const val CANCEL_LOCK_WAIT_MS = 1_000L
        private const val LOCK_WAIT_SLACK_MS = 100L
        private const val SERVER_LOCK_WAIT_MS = 20_000L
    }
}
