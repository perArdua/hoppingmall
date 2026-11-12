package com.hoppingmall.user.service

import com.hoppingmall.common.BusinessException
import com.hoppingmall.user.common.enums.Role
import com.hoppingmall.user.common.vo.Email
import com.hoppingmall.user.common.vo.Password
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
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@DisplayName("판매자 신청 승인·거절 동시 처리 MySQL 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class SellerApprovalConcurrencyMySqlIntegrationTest : MySqlJpaTestSupport() {

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var sellerRepository: SellerRepository

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    @Test
    fun 승인이_커밋되기_전에_거절이_오면_승인만_반영되고_거절은_409로_끝난다() {
        val user = savedBuyer("approve-first@example.com")
        val sellerId = savedPendingSeller(user, "111-11-11111")

        val (first, second) = decideConcurrently(sellerId, "APPROVED", "REJECTED")

        assertThat(listOf(first.isSuccess, second.isSuccess, approvalStatusOf(sellerId), roleOf(user.id!!)))
            .describedAs("첫째=%s, 둘째=%s", first, second)
            .containsExactly(true, false, "APPROVED", "SELLER")
        assertThat((second.exceptionOrNull() as BusinessException).errorCode.status).isEqualTo(HttpStatus.CONFLICT)
    }

    @Test
    fun 거절이_커밋되기_전에_승인이_오면_거절만_반영되고_승인은_409로_끝나며_역할은_바뀌지_않는다() {
        val user = savedBuyer("reject-first@example.com")
        val sellerId = savedPendingSeller(user, "222-22-22222")

        val (first, second) = decideConcurrently(sellerId, "REJECTED", "APPROVED")

        assertThat(listOf(first.isSuccess, second.isSuccess, approvalStatusOf(sellerId), roleOf(user.id!!)))
            .describedAs("첫째=%s, 둘째=%s", first, second)
            .containsExactly(true, false, "REJECTED", "BUYER")
        assertThat((second.exceptionOrNull() as BusinessException).errorCode.status).isEqualTo(HttpStatus.CONFLICT)
    }

    private fun decideConcurrently(sellerId: Long, firstStatus: String, secondStatus: String): Pair<Result<Unit>, Result<Unit>> {
        val service = adminCommandService()
        val transactionTemplate = TransactionTemplate(transactionManager)
        val firstDecided = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)

        val first = executor.submit<Result<Unit>> {
            runCatching {
                transactionTemplate.executeWithoutResult {
                    service.updateSellerApprovalStatus(sellerId, SellerApprovalRequest(firstStatus))
                    sellerRepository.flush()
                    firstDecided.countDown()
                    releaseFirst.await(30, TimeUnit.SECONDS)
                }
            }
        }
        assertThat(firstDecided.await(15, TimeUnit.SECONDS)).isTrue()

        val second = executor.submit<Result<Unit>> {
            runCatching {
                transactionTemplate.executeWithoutResult {
                    service.updateSellerApprovalStatus(sellerId, SellerApprovalRequest(secondStatus))
                }
            }
        }
        awaitLockWaitOrCompletion(second, "두 번째 결정", "sellers")
        releaseFirst.countDown()

        return first.get(30, TimeUnit.SECONDS) to second.get(30, TimeUnit.SECONDS)
    }

    private fun adminCommandService() = AdminCommandServiceImpl(
        sellerRepository = sellerRepository,
        commandMapper = SellerApprovalCommandMapper(ApproveSellerCommand(), RejectSellerCommand()),
        userRepository = userRepository
    )

    private fun savedBuyer(email: String): User =
        userRepository.save(User.create(Email(email), Password("encoded-password"), "판매자신청자", Role.BUYER))

    private fun savedPendingSeller(user: User, businessNumber: String): Long =
        sellerRepository.save(Seller.create(user, businessNumber)).id!!

    private fun approvalStatusOf(sellerId: Long): String =
        jdbcTemplate.queryForObject("SELECT approval_status FROM sellers WHERE id = ?", String::class.java, sellerId)!!

    private fun roleOf(userId: Long): String =
        jdbcTemplate.queryForObject("SELECT role FROM users WHERE id = ?", String::class.java, userId)!!
}
