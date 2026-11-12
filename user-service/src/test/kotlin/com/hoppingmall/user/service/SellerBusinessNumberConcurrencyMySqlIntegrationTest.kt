package com.hoppingmall.user.service

import com.hoppingmall.common.BusinessException
import com.hoppingmall.user.common.enums.Role
import com.hoppingmall.user.common.vo.Email
import com.hoppingmall.user.common.vo.Password
import com.hoppingmall.user.domain.Seller
import com.hoppingmall.user.domain.User
import com.hoppingmall.user.domain.repository.SellerRepository
import com.hoppingmall.user.domain.repository.UserRepository
import com.hoppingmall.user.dto.request.SellerApplyRequest
import com.hoppingmall.user.exception.seller.SellerBusinessNumberDuplicateException
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

@DisplayName("같은 사업자 번호의 판매자 신청 동시 처리 MySQL 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class SellerBusinessNumberConcurrencyMySqlIntegrationTest : MySqlJpaTestSupport() {

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var sellerRepository: SellerRepository

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    @Test
    fun 서로_다른_사용자가_같은_사업자_번호로_동시에_신청하면_늦은_신청은_409_S002로_끝난다() {
        val firstUserId = saveUser("first-applicant@example.com")
        val secondUserId = saveUser("second-applicant@example.com")

        val (firstResult, secondResult) = applyConcurrently(firstUserId, secondUserId, "333-33-33333")

        assertThat(listOf(firstResult.isSuccess, secondResult.isSuccess, countByBusinessNumber("333-33-33333")))
            .describedAs("첫째=%s, 둘째=%s", firstResult, secondResult)
            .containsExactly(true, false, 1L)
        assertBusinessNumberDuplicated(secondResult)
        assertThat(sellerRepository.findNullableByUserId(firstUserId)?.businessNumber).isEqualTo("333-33-33333")
        assertThat(sellerRepository.findNullableByUserId(secondUserId)).isNull()
    }

    @Test
    fun 거절된_사용자의_재신청이_다른_사용자의_신청과_같은_사업자_번호로_동시에_오면_늦은_재신청은_409_S002로_끝난다() {
        val applicantId = saveUser("new-applicant@example.com")
        val rejectedUserId = saveUser("rejected-applicant@example.com")
        val rejectedUser = userRepository.findNullableById(rejectedUserId)!!
        sellerRepository.save(Seller.create(rejectedUser, "444-44-44444").apply { reject() })

        val (firstResult, secondResult) = applyConcurrently(applicantId, rejectedUserId, "555-55-55555")

        assertThat(listOf(firstResult.isSuccess, secondResult.isSuccess, countByBusinessNumber("555-55-55555")))
            .describedAs("첫째=%s, 둘째=%s", firstResult, secondResult)
            .containsExactly(true, false, 1L)
        assertBusinessNumberDuplicated(secondResult)
        val rejected = sellerRepository.findNullableByUserId(rejectedUserId)!!
        assertThat(listOf(rejected.businessNumber, rejected.getApprovalStatus()))
            .containsExactly("444-44-44444", Seller.ApprovalStatus.REJECTED)
    }

    private fun applyConcurrently(firstUserId: Long, secondUserId: Long, businessNumber: String): Pair<Result<Unit>, Result<Unit>> {
        val service = SellerCommandServiceImpl(userRepository, sellerRepository)
        val transactionTemplate = TransactionTemplate(transactionManager)
        val firstApplied = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)

        val first = executor.submit<Result<Unit>> {
            runCatching {
                transactionTemplate.executeWithoutResult {
                    service.apply(firstUserId, SellerApplyRequest(businessNumber))
                    sellerRepository.flush()
                    firstApplied.countDown()
                    releaseFirst.await(30, TimeUnit.SECONDS)
                }
            }
        }
        assertThat(firstApplied.await(15, TimeUnit.SECONDS)).isTrue()

        val second = executor.submit<Result<Unit>> {
            runCatching {
                transactionTemplate.executeWithoutResult {
                    service.apply(secondUserId, SellerApplyRequest(businessNumber))
                }
            }
        }
        awaitLockWaitOrCompletion(second, "두 번째 신청", "sellers")
        releaseFirst.countDown()
        return first.get(30, TimeUnit.SECONDS) to second.get(30, TimeUnit.SECONDS)
    }

    private fun assertBusinessNumberDuplicated(result: Result<Unit>) {
        assertThat(result.exceptionOrNull()).isInstanceOf(SellerBusinessNumberDuplicateException::class.java)
        assertThat((result.exceptionOrNull() as BusinessException).errorCode.status).isEqualTo(HttpStatus.CONFLICT)
    }

    private fun saveUser(email: String): Long =
        userRepository.save(User.create(Email(email), Password("encoded-password"), "판매자신청자", Role.BUYER)).id!!

    private fun countByBusinessNumber(businessNumber: String): Long =
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sellers WHERE business_number = ?", Long::class.java, businessNumber)!!
}
