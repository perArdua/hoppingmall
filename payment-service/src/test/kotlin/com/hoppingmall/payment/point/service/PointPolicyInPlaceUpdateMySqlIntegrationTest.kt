package com.hoppingmall.payment.point.service

import com.hoppingmall.common.BusinessException
import com.hoppingmall.payment.point.domain.PointPolicyRepository
import com.hoppingmall.payment.point.dto.request.PointPolicyRequest
import com.hoppingmall.payment.support.MySqlJpaTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal

@DisplayName("포인트 정책 수정·켜기·끄기가 같은 행을 고침 (실제 MySQL)")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class PointPolicyInPlaceUpdateMySqlIntegrationTest : MySqlJpaTestSupport() {

    @Autowired
    private lateinit var pointPolicyRepository: PointPolicyRepository

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    private lateinit var transactionTemplate: TransactionTemplate

    private lateinit var service: PointPolicyServiceImpl

    @BeforeEach
    fun setUp() {
        jdbcTemplate.update("DELETE FROM point_policies")
        transactionTemplate = TransactionTemplate(transactionManager)
        service = PointPolicyServiceImpl(pointPolicyRepository)
    }

    @Test
    fun 활성_정책이_있을_때_두_번째_정책을_만들면_앞_정책은_꺼지고_새_정책만_켜진다() {
        val first = inTx { service.createPolicy(request("기본 정책")) }

        val second = inTx { service.createPolicy(request("행사 정책")) }

        assertThat(rows()).containsExactlyInAnyOrder(
            Row(first.id, "기본 정책", false),
            Row(second.id, "행사 정책", true)
        )
    }

    @Test
    fun 정책을_끄면_같은_행이_꺼지고_행은_늘지_않는다() {
        val created = inTx { service.createPolicy(request("기본 정책")) }

        val result = inTx { service.deactivatePolicy(created.id) }

        assertThat(result.id).isEqualTo(created.id)
        assertThat(result.isActive).isFalse()
        assertThat(rows()).containsExactly(Row(created.id, "기본 정책", false))
    }

    @Test
    fun 꺼진_정책을_켜면_같은_행이_켜지고_다른_활성_정책은_꺼진다() {
        val first = inTx { service.createPolicy(request("기본 정책")) }
        val second = inTx { service.createPolicy(request("행사 정책")) }

        val result = inTx { service.activatePolicy(first.id) }

        assertThat(result.id).isEqualTo(first.id)
        assertThat(result.isActive).isTrue()
        assertThat(rows()).containsExactlyInAnyOrder(
            Row(first.id, "기본 정책", true),
            Row(second.id, "행사 정책", false)
        )
    }

    @Test
    fun 이미_켜진_정책을_다시_켜도_그_정책이_켜진_채로_남는다() {
        val created = inTx { service.createPolicy(request("기본 정책")) }

        val result = inTx { service.activatePolicy(created.id) }

        assertThat(result.isActive).isTrue()
        assertThat(rows()).containsExactly(Row(created.id, "기본 정책", true))
    }

    @Test
    fun 이름을_그대로_두고_수정하면_같은_행의_값이_바뀐다() {
        val created = inTx { service.createPolicy(request("기본 정책")) }

        val result = inTx { service.updatePolicy(created.id, request("기본 정책", earnRate = "0.03")) }

        assertThat(result.id).isEqualTo(created.id)
        assertThat(result.earnRate).isEqualByComparingTo(BigDecimal("0.03"))
        assertThat(rows()).containsExactly(Row(created.id, "기본 정책", true))
        assertThat(earnRateOf(created.id)).isEqualByComparingTo(BigDecimal("0.03"))
    }

    @Test
    fun 이름을_바꿔_수정하면_새_행을_만들지_않고_같은_행의_이름이_바뀐다() {
        val created = inTx { service.createPolicy(request("기본 정책")) }

        val result = inTx { service.updatePolicy(created.id, request("새 이름 정책")) }

        assertThat(result.id).isEqualTo(created.id)
        assertThat(rows()).containsExactly(Row(created.id, "새 이름 정책", true))
    }

    @Test
    fun 다른_정책의_이름으로_수정하면_충돌로_거절하고_두_정책은_그대로다() {
        val first = inTx { service.createPolicy(request("기본 정책")) }
        val second = inTx { service.createPolicy(request("행사 정책")) }

        val thrown = catchThrowable { inTx { service.updatePolicy(second.id, request("기본 정책")) } }

        assertThat(thrown).isInstanceOf(BusinessException::class.java)
        assertThat((thrown as BusinessException).errorCode.status).isEqualTo(HttpStatus.CONFLICT)
        assertThat(rows()).containsExactlyInAnyOrder(
            Row(first.id, "기본 정책", false),
            Row(second.id, "행사 정책", true)
        )
    }

    private fun <T> inTx(action: () -> T): T = transactionTemplate.execute { action() }!!

    private fun rows(): List<Row> =
        jdbcTemplate.query("SELECT id, policy_name, is_active FROM point_policies") { rs, _ ->
            Row(rs.getLong("id"), rs.getString("policy_name"), rs.getBoolean("is_active"))
        }

    private fun earnRateOf(id: Long): BigDecimal =
        jdbcTemplate.queryForObject("SELECT earn_rate FROM point_policies WHERE id = ?", BigDecimal::class.java, id)!!

    private fun request(name: String, earnRate: String = "0.01") = PointPolicyRequest(
        policyName = name,
        earnRate = BigDecimal(earnRate),
        maxEarnRate = BigDecimal("0.05"),
        minUseAmount = BigDecimal("100"),
        maxUseAmount = BigDecimal("10000"),
        description = null
    )

    private data class Row(val id: Long, val name: String, val active: Boolean)
}
