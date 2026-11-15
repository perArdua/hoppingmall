package com.hoppingmall.user.service

import com.hoppingmall.common.BusinessException
import com.hoppingmall.common.CommonErrorCode
import com.hoppingmall.user.common.enums.Role
import com.hoppingmall.user.common.vo.Email
import com.hoppingmall.user.common.vo.Password
import com.hoppingmall.user.config.JpaConfig
import com.hoppingmall.user.domain.Seller
import com.hoppingmall.user.domain.User
import com.hoppingmall.user.domain.repository.SellerRepository
import com.hoppingmall.user.domain.repository.UserRepository
import com.hoppingmall.user.dto.request.SellerApprovalRequest
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
import java.util.concurrent.atomic.AtomicInteger

@DisplayName("판매자 신청 목록 신청자 검색 MySQL 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@Import(JpaConfig::class)
class SellerApplicationSearchMySqlIntegrationTest : MySqlJpaTestSupport() {

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var sellerRepository: SellerRepository

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    private val transaction by lazy { TransactionTemplate(transactionManager) }

    private val queryService by lazy { SellerQueryServiceImpl(sellerRepository, userRepository) }

    private val adminService by lazy {
        AdminCommandServiceImpl(sellerRepository, SellerApprovalCommandMapper(ApproveSellerCommand(), RejectSellerCommand()), userRepository)
    }

    private val businessNumbers = AtomicInteger()

    @Test
    fun 이름_일부로_찾으면_그_이름이_들어간_신청자의_신청만_나온다() {
        val kim = apply("kim@example.com", "김하늘")
        apply("lee@example.com", "이바다")
        val kimSecond = apply("sky@example.com", "박하늘")

        val found = queryService.getApplications(null, 0, 20, "하늘")

        assertThat(found.content.map { it.id }).containsExactly(kim, kimSecond)
        assertThat(found.totalElements).isEqualTo(2L)
    }

    @Test
    fun 이메일_일부로_찾고_대소문자를_가리지_않는다() {
        val target = apply("Seller.Kim@Example.com", "판매자")
        apply("buyer@sample.org", "구매자")

        val found = queryService.getApplications(null, 0, 20, "SELLER.kim@")

        assertThat(found.content.map { it.id }).containsExactly(target)
        assertThat(found.content.single().userEmail).isEqualTo("Seller.Kim@Example.com")
    }

    @Test
    fun 상태와_검색어를_함께_쓰면_둘_다_맞는_신청만_세고_나눈다() {
        val pendings = (1..3).map { apply("shop$it@example.com", "가게$it") }
        val approved = apply("shop9@example.com", "가게9")
        transaction.executeWithoutResult { adminService.updateSellerApprovalStatus(approved, SellerApprovalRequest("APPROVED")) }
        apply("other@example.com", "다른곳")

        val firstPage = queryService.getApplications(Seller.ApprovalStatus.PENDING, 0, 2, "shop")
        val secondPage = queryService.getApplications(Seller.ApprovalStatus.PENDING, 1, 2, "shop")
        val approvedOnly = queryService.getApplications(Seller.ApprovalStatus.APPROVED, 0, 20, "가게")

        assertThat(firstPage.content.map { it.id }).containsExactly(pendings[0], pendings[1])
        assertThat(secondPage.content.map { it.id }).containsExactly(pendings[2])
        assertThat(firstPage.totalElements).isEqualTo(3L)
        assertThat(firstPage.totalPages).isEqualTo(2)
        assertThat(approvedOnly.content.map { it.id }).containsExactly(approved)
    }

    @Test
    fun 퍼센트와_밑줄과_느낌표는_문자_그대로_찾는다() {
        val underscore = apply("a_b@example.com", "밑줄")
        apply("axb@example.com", "엑스")
        val percent = apply("p1@example.com", "100%할인")
        apply("p2@example.com", "100원할인")
        val bang = apply("p3@example.com", "와!세일")
        apply("p4@example.com", "와세일")

        assertThat(queryService.getApplications(null, 0, 20, "a_b").content.map { it.id }).containsExactly(underscore)
        assertThat(queryService.getApplications(null, 0, 20, "100%").content.map { it.id }).containsExactly(percent)
        assertThat(queryService.getApplications(null, 0, 20, "와!").content.map { it.id }).containsExactly(bang)
        assertThat(queryService.getApplications(null, 0, 20, "%").content.map { it.id }).containsExactly(percent)
    }

    @Test
    fun 비었거나_공백뿐인_검색어는_거르지_않고_앞뒤_공백은_뗀다() {
        val first = apply("one@example.com", "하나")
        val second = apply("two@example.com", "둘")

        assertThat(queryService.getApplications(null, 0, 20, "   ").content.map { it.id }).containsExactly(first, second)
        assertThat(queryService.getApplications(null, 0, 20, "").totalElements).isEqualTo(2L)
        assertThat(queryService.getApplications(null, 0, 20, "  하나 ").content.map { it.id }).containsExactly(first)
    }

    @Test
    fun 맞는_신청자가_없으면_빈_페이지다() {
        apply("one@example.com", "하나")

        val found = queryService.getApplications(null, 0, 20, "없는사람")

        assertThat(found.content).isEmpty()
        assertThat(found.totalElements).isZero()
    }

    @Test
    fun 백_자를_넘는_검색어는_400_C001이다() {
        assertThat(queryService.getApplications(null, 0, 20, "가".repeat(100)).content).isEmpty()
        assertThatThrownBy { queryService.getApplications(null, 0, 20, "가".repeat(101)) }
            .isInstanceOfSatisfying(BusinessException::class.java) {
                assertThat(it.errorCode).isEqualTo(CommonErrorCode.INVALID_INPUT)
            }
    }

    private fun apply(email: String, name: String): Long {
        val user = userRepository.save(User.create(Email(email), Password("encoded-password"), name, Role.BUYER))
        val number = businessNumbers.incrementAndGet()
        return sellerRepository.save(Seller.create(user, "303-03-%05d".format(number))).id!!
    }
}
