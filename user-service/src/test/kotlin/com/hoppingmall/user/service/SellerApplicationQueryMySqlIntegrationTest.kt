package com.hoppingmall.user.service

import com.hoppingmall.user.common.enums.Role
import com.hoppingmall.user.common.vo.Email
import com.hoppingmall.user.common.vo.Password
import com.hoppingmall.user.config.JpaConfig
import com.hoppingmall.user.domain.Seller
import com.hoppingmall.user.domain.User
import com.hoppingmall.user.domain.repository.SellerRepository
import com.hoppingmall.user.domain.repository.UserRepository
import com.hoppingmall.user.dto.request.SellerApplyRequest
import com.hoppingmall.user.dto.request.SellerApprovalRequest
import com.hoppingmall.user.exception.seller.SellerNotFoundException
import com.hoppingmall.user.service.strategy.ApproveSellerCommand
import com.hoppingmall.user.service.strategy.RejectSellerCommand
import com.hoppingmall.user.service.strategy.SellerApprovalCommandMapper
import com.hoppingmall.user.support.MySqlJpaTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

@DisplayName("판매자 신청 조회·거절 사유 MySQL 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@Import(JpaConfig::class)
class SellerApplicationQueryMySqlIntegrationTest : MySqlJpaTestSupport() {

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var sellerRepository: SellerRepository

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    private val transaction by lazy { TransactionTemplate(transactionManager) }

    private val commandService by lazy { SellerCommandServiceImpl(userRepository, sellerRepository) }

    private val queryService by lazy { SellerQueryServiceImpl(sellerRepository, userRepository) }

    private val adminService by lazy {
        AdminCommandServiceImpl(sellerRepository, SellerApprovalCommandMapper(ApproveSellerCommand(), RejectSellerCommand()), userRepository)
    }

    @Test
    fun 신청하면_신청_id와_대기_상태를_돌려주고_내_신청으로_조회된다() {
        val userId = saveUser("apply@example.com")

        val applied = transaction.execute { commandService.apply(userId, SellerApplyRequest("101-01-00001")) }!!
        val mine = queryService.getMyApplication(userId)

        assertThat(applied.id).isPositive()
        assertThat(applied.status).isEqualTo(Seller.ApprovalStatus.PENDING)
        assertThat(mine.id).isEqualTo(applied.id)
        assertThat(mine.businessNumber).isEqualTo("101-01-00001")
    }

    @Test
    fun 신청하지_않은_사용자의_내_신청은_S004다() {
        val userId = saveUser("none@example.com")

        assertThatThrownBy { queryService.getMyApplication(userId) }.isInstanceOf(SellerNotFoundException::class.java)
    }

    @Test
    fun 거절_사유를_남기면_신청자가_보고_다시_신청하면_지워진다() {
        val userId = saveUser("reject@example.com")
        val applied = transaction.execute { commandService.apply(userId, SellerApplyRequest("101-01-00002")) }!!

        transaction.executeWithoutResult {
            adminService.updateSellerApprovalStatus(applied.id, SellerApprovalRequest("REJECTED", "  사업자등록증이 흐립니다  "))
        }
        val rejected = queryService.getMyApplication(userId)
        transaction.execute { commandService.apply(userId, SellerApplyRequest("101-01-00003")) }
        val reapplied = queryService.getMyApplication(userId)

        assertThat(rejected.status).isEqualTo(Seller.ApprovalStatus.REJECTED)
        assertThat(rejected.rejectionReason).isEqualTo("사업자등록증이 흐립니다")
        assertThat(reapplied.status).isEqualTo(Seller.ApprovalStatus.PENDING)
        assertThat(reapplied.rejectionReason).isNull()
    }

    @Test
    fun 관리자_목록은_상태로_거르고_신청_순서대로_페이지를_나누며_신청자_이메일과_이름을_싣는다() {
        val ids = (1..3).map { n ->
            val userId = saveUser("pending$n@example.com")
            transaction.execute { commandService.apply(userId, SellerApplyRequest("202-02-0000$n")) }!!.id
        }
        val approvedUser = saveUser("approved@example.com")
        val approved = transaction.execute { commandService.apply(approvedUser, SellerApplyRequest("202-02-00009")) }!!
        transaction.executeWithoutResult { adminService.updateSellerApprovalStatus(approved.id, SellerApprovalRequest("APPROVED")) }

        val firstPage = queryService.getApplications(Seller.ApprovalStatus.PENDING, 0, 2)
        val secondPage = queryService.getApplications(Seller.ApprovalStatus.PENDING, 1, 2)
        val all = queryService.getApplications(null, 0, 20)

        assertThat(firstPage.content.map { it.id }).containsExactly(ids[0], ids[1])
        assertThat(secondPage.content.map { it.id }).containsExactly(ids[2])
        assertThat(firstPage.totalElements).isEqualTo(3L)
        assertThat(firstPage.totalPages).isEqualTo(2)
        assertThat(firstPage.content.first().userEmail).isEqualTo("pending1@example.com")
        assertThat(firstPage.content.first().userName).isEqualTo("신청자")
        assertThat(all.content.map { it.status }).contains(Seller.ApprovalStatus.APPROVED)
        assertThat(all.totalElements).isEqualTo(4L)
    }

    @Test
    fun 페이지_크기는_1에서_100_사이로_자른다() {
        assertThat(queryService.getApplications(null, -1, 1000).size).isEqualTo(100)
        assertThat(queryService.getApplications(null, 0, 0).size).isEqualTo(1)
        assertThat(queryService.getApplications(null, -3, 10).page).isEqualTo(0)
    }

    @Test
    fun 상태별_목록_조회는_상태_인덱스를_탄다() {
        val plan = jdbcTemplate.queryForMap(
            "EXPLAIN SELECT id FROM sellers WHERE approval_status = 'PENDING' ORDER BY id LIMIT 20"
        )

        assertThat(plan["key"]).isEqualTo("idx_sellers_approval_status")
    }

    private fun saveUser(email: String): Long =
        userRepository.save(User.create(Email(email), Password("encoded-password"), "신청자", Role.BUYER)).id!!
}
