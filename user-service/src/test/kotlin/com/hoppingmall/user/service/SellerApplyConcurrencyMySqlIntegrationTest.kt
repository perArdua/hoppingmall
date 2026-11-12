package com.hoppingmall.user.service

import com.hoppingmall.common.BusinessException
import com.hoppingmall.user.common.enums.Role
import com.hoppingmall.user.common.vo.Email
import com.hoppingmall.user.common.vo.Password
import com.hoppingmall.user.domain.User
import com.hoppingmall.user.domain.repository.SellerRepository
import com.hoppingmall.user.domain.repository.UserRepository
import com.hoppingmall.user.dto.request.SellerApplyRequest
import com.hoppingmall.user.exception.seller.SellerAlreadyAppliedException
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

@DisplayName("같은 사용자의 판매자 신청 동시 처리 MySQL 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class SellerApplyConcurrencyMySqlIntegrationTest : MySqlJpaTestSupport() {

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var sellerRepository: SellerRepository

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    @Test
    fun 같은_사용자의_신청이_동시에_오면_행은_하나이고_늦은_신청은_409로_끝난다() {
        val userId = userRepository.save(
            User.create(Email("apply-twice@example.com"), Password("encoded-password"), "판매자신청자", Role.BUYER)
        ).id!!
        val service = SellerCommandServiceImpl(userRepository, sellerRepository)
        val transactionTemplate = TransactionTemplate(transactionManager)
        val firstApplied = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)

        val first = executor.submit<Result<Unit>> {
            runCatching {
                transactionTemplate.executeWithoutResult {
                    service.apply(userId, SellerApplyRequest("111-11-11111"))
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
                    service.apply(userId, SellerApplyRequest("222-22-22222"))
                }
            }
        }
        awaitLockWaitOrCompletion(second, "두 번째 신청", "sellers")
        releaseFirst.countDown()
        val firstResult = first.get(30, TimeUnit.SECONDS)
        val secondResult = second.get(30, TimeUnit.SECONDS)

        val rows = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sellers WHERE user_id = ?", Long::class.java, userId)
        assertThat(listOf(firstResult.isSuccess, secondResult.isSuccess, rows))
            .describedAs("첫째=%s, 둘째=%s", firstResult, secondResult)
            .containsExactly(true, false, 1L)
        assertThat(secondResult.exceptionOrNull()).isInstanceOf(SellerAlreadyAppliedException::class.java)
        assertThat((secondResult.exceptionOrNull() as BusinessException).errorCode.status).isEqualTo(HttpStatus.CONFLICT)
        assertThat(sellerRepository.findNullableByUserId(userId)?.businessNumber).isEqualTo("111-11-11111")
    }
}
