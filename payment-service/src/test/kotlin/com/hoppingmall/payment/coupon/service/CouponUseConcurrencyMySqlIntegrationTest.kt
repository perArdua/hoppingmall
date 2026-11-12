package com.hoppingmall.payment.coupon.service

import com.hoppingmall.payment.coupon.domain.Coupon
import com.hoppingmall.payment.coupon.domain.UserCoupon
import com.hoppingmall.payment.coupon.domain.repository.CouponRepository
import com.hoppingmall.payment.coupon.domain.repository.UserCouponRepository
import com.hoppingmall.payment.coupon.enum.DiscountType
import com.hoppingmall.payment.coupon.exception.CouponNotAvailableException
import com.hoppingmall.payment.support.MySqlJpaTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

@DisplayName("쿠폰 동시 사용 MySQL 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class CouponUseConcurrencyMySqlIntegrationTest : MySqlJpaTestSupport() {

    @Autowired
    private lateinit var couponRepository: CouponRepository

    @Autowired
    private lateinit var userCouponRepository: UserCouponRepository

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    @Test
    fun 같은_쿠폰을_두_결제가_동시에_쓰면_먼저_잡은_결제만_성공하고_쿠폰의_주문은_그_결제의_주문이다() {
        val couponId = savedCouponIssuedTo(USER_ID)
        val service = couponCommandService()
        val transactionTemplate = TransactionTemplate(transactionManager)
        val firstUsed = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)

        val first = executor.submit<Result<BigDecimal>> {
            runCatching {
                transactionTemplate.execute {
                    val discount = service.useCoupon(USER_ID, couponId, ORDER_AMOUNT, FIRST_ORDER_ID)
                    userCouponRepository.flush()
                    firstUsed.countDown()
                    releaseFirst.await(30, TimeUnit.SECONDS)
                    discount
                }!!
            }
        }
        assertThat(firstUsed.await(15, TimeUnit.SECONDS)).isTrue()

        val enteredAt = AtomicLong(0L)
        val second = executor.submit<Result<BigDecimal>> {
            enteredAt.set(System.currentTimeMillis())
            runCatching {
                transactionTemplate.execute {
                    service.useCoupon(USER_ID, couponId, ORDER_AMOUNT, SECOND_ORDER_ID)
                }!!
            }
        }
        awaitLockWait(second, enteredAt, "두 번째 쿠폰 사용", "user_coupons")
        releaseFirst.countDown()

        val firstResult = first.get(30, TimeUnit.SECONDS)
        val secondResult = second.get(30, TimeUnit.SECONDS)

        assertThat(listOf(firstResult, secondResult).count { it.isSuccess })
            .describedAs("성공한 쿠폰 사용 수 (첫째=%s, 둘째=%s)", firstResult, secondResult)
            .isEqualTo(1)
        assertThat(firstResult.getOrNull()).isEqualByComparingTo(BigDecimal("1000"))
        assertThat(secondResult.exceptionOrNull()).isInstanceOf(CouponNotAvailableException::class.java)
        assertThat(
            jdbcTemplate.queryForMap("SELECT status, order_id FROM user_coupons WHERE coupon_id = ?", couponId)
        ).containsEntry("status", "USED").containsEntry("order_id", FIRST_ORDER_ID)
    }

    private fun couponCommandService() = CouponCommandServiceImpl(
        couponRepository,
        userCouponRepository,
        mock(),
        mock(),
        mock()
    )

    private fun savedCouponIssuedTo(userId: Long): Long {
        val coupon = couponRepository.save(
            Coupon.create(
                name = "동시 사용 쿠폰",
                code = "CONCURRENT-USE",
                discountType = DiscountType.FIXED_AMOUNT,
                discountValue = BigDecimal("1000"),
                minOrderAmount = BigDecimal("10000"),
                maxDiscountAmount = null,
                totalQuantity = 10,
                validFrom = LocalDateTime.now().minusDays(1),
                validTo = LocalDateTime.now().plusDays(30)
            )
        )
        userCouponRepository.save(UserCoupon.create(userId = userId, couponId = coupon.id!!))
        return coupon.id!!
    }

    companion object {
        private const val USER_ID = 10L
        private const val FIRST_ORDER_ID = 100L
        private const val SECOND_ORDER_ID = 200L
        private val ORDER_AMOUNT = BigDecimal("30000")
    }
}
