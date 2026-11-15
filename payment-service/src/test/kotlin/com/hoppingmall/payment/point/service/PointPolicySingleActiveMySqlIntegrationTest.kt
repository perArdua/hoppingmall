package com.hoppingmall.payment.point.service

import com.hoppingmall.common.BusinessException
import com.hoppingmall.payment.point.domain.PointPolicyRepository
import com.hoppingmall.payment.point.dto.request.PointPolicyRequest
import com.hoppingmall.payment.support.MySqlJpaTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.util.concurrent.Callable
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import javax.sql.DataSource

@DisplayName("포인트 정책 활성은 동시에 켜거나 만들어도 하나 (실제 MySQL)")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class PointPolicySingleActiveMySqlIntegrationTest : MySqlJpaTestSupport() {

    @Autowired
    private lateinit var pointPolicyRepository: PointPolicyRepository

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    @Autowired
    private lateinit var dataSource: DataSource

    private lateinit var transactionTemplate: TransactionTemplate

    private lateinit var service: PointPolicyServiceImpl

    @BeforeEach
    fun setUp() {
        jdbcTemplate.update("DELETE FROM point_policies")
        transactionTemplate = TransactionTemplate(transactionManager)
        service = PointPolicyServiceImpl(pointPolicyRepository)
    }

    @Test
    fun 두_관리자가_동시에_다른_정책을_켜도_활성_정책은_하나다() {
        val first = inTx { service.createPolicy(request("첫 정책")) }
        val second = inTx { service.createPolicy(request("둘째 정책")) }
        val current = inTx { service.createPolicy(request("지금 정책")) }

        val outcomes = runWhileHoldingRow(current.id, listOf(
            Callable { runCatching { inTx { service.activatePolicy(first.id) } } },
            Callable { runCatching { inTx { service.activatePolicy(second.id) } } }
        ))

        assertOnlyConflictFailures(outcomes)
        assertThat(activeCount()).isEqualTo(1)
        assertThat(runCatching { inTx { service.getCurrentPolicy() } }.isSuccess).isTrue()
    }

    @Test
    fun 두_관리자가_동시에_새_정책을_만들어도_활성_정책은_하나다() {
        val current = inTx { service.createPolicy(request("지금 정책")) }

        val outcomes = runWhileHoldingRow(current.id, listOf(
            Callable { runCatching { inTx { service.createPolicy(request("행사 정책 가")) } } },
            Callable { runCatching { inTx { service.createPolicy(request("행사 정책 나")) } } }
        ))

        assertOnlyConflictFailures(outcomes)
        assertThat(activeCount()).isEqualTo(1)
        assertThat(runCatching { inTx { service.getCurrentPolicy() } }.isSuccess).isTrue()
    }

    @Test
    fun 활성_정책_두_행은_DB가_받지_않는다() {
        insertRow("가 정책", active = true)

        assertThatThrownBy { insertRow("나 정책", active = true) }
            .isInstanceOf(DataIntegrityViolationException::class.java)
        insertRow("다 정책", active = false)
        insertRow("라 정책", active = false)
        assertThat(activeCount()).isEqualTo(1)
    }

    private fun runWhileHoldingRow(heldId: Long, actions: List<Callable<Result<*>>>): List<Result<*>> {
        val holder = dataSource.connection
        holder.autoCommit = false
        try {
            holder.prepareStatement("SELECT id FROM point_policies WHERE id = ? FOR UPDATE").use {
                it.setLong(1, heldId)
                it.executeQuery().close()
            }
            val futures: List<Future<Result<*>>> = actions.map { executor.submit(it) }
            awaitWaiters(actions.size, futures)
            holder.commit()
            return futures.map { it.get(30, TimeUnit.SECONDS) }
        } finally {
            if (!holder.isClosed) {
                holder.rollback()
                holder.close()
            }
        }
    }

    private fun awaitWaiters(expected: Int, futures: List<Future<*>>) {
        val deadline = System.currentTimeMillis() + 15_000L
        while (System.currentTimeMillis() < deadline) {
            if (lockWaitCount("point_policies") >= expected) return
            if (futures.any { it.isDone }) {
                throw AssertionError("잠금을 기다리지 않고 끝난 요청이 있다: " + futures.filter { it.isDone }.map { it.get() })
            }
            Thread.onSpinWait()
        }
        throw AssertionError("point_policies 잠금 대기 ${expected}개가 15초 안에 보이지 않았다")
    }

    private fun assertOnlyConflictFailures(outcomes: List<Result<*>>) {
        outcomes.mapNotNull { it.exceptionOrNull() }.forEach {
            assertThat(it).isInstanceOf(BusinessException::class.java)
            val errorCode = (it as BusinessException).errorCode
            assertThat(errorCode.status).isEqualTo(HttpStatus.CONFLICT)
            assertThat(errorCode.code).isEqualTo("PNT005")
        }
    }

    private fun <T> inTx(action: () -> T): T = transactionTemplate.execute { action() }!!

    private fun activeCount(): Int =
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM point_policies WHERE is_active = 1", Int::class.java)!!

    private fun insertRow(name: String, active: Boolean) {
        jdbcTemplate.update(
            """
            INSERT INTO point_policies (policy_name, earn_rate, max_earn_rate, min_use_amount, max_use_amount, is_active, created_at)
            VALUES (?, 0.01, 0.05, 100, 10000, ?, NOW())
            """.trimIndent(),
            name, active
        )
    }

    private fun request(name: String) = PointPolicyRequest(
        policyName = name,
        earnRate = BigDecimal("0.01"),
        maxEarnRate = BigDecimal("0.05"),
        minUseAmount = BigDecimal("100"),
        maxUseAmount = BigDecimal("10000"),
        description = null
    )
}
