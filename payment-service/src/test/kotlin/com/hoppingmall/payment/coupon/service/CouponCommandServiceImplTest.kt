package com.hoppingmall.payment.coupon.service

import com.hoppingmall.common.BaseEntity
import com.hoppingmall.payment.coupon.domain.Coupon
import com.hoppingmall.payment.coupon.domain.UserCoupon
import com.hoppingmall.payment.coupon.domain.repository.CouponRepository
import com.hoppingmall.payment.coupon.domain.repository.UserCouponRepository
import com.hoppingmall.payment.coupon.dto.event.CouponRestoreEvent
import com.hoppingmall.payment.coupon.dto.request.CouponCreateRequest
import com.hoppingmall.payment.coupon.enum.CouponRestoreReason
import com.hoppingmall.payment.coupon.enum.CouponStatus
import com.hoppingmall.payment.coupon.enum.DiscountType
import com.hoppingmall.payment.coupon.enum.UserCouponStatus
import com.hoppingmall.payment.coupon.exception.*
import com.hoppingmall.payment.coupon.infrastructure.CouponReserveResult
import com.hoppingmall.payment.coupon.infrastructure.CouponStockRedisRepository
import com.hoppingmall.payment.coupon.metrics.CouponCompensationMetrics
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.mockStatic
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.Optional

@DisplayName("CouponCommandServiceImpl")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class CouponCommandServiceImplTest {

    @Mock
    private lateinit var couponRepository: CouponRepository

    @Mock
    private lateinit var userCouponRepository: UserCouponRepository

    @Mock
    private lateinit var couponStockRedisRepository: CouponStockRedisRepository

    @Mock
    private lateinit var compensationPublisher: CouponCompensationPublisher

    @Mock
    private lateinit var compensationMetrics: CouponCompensationMetrics

    @InjectMocks
    private lateinit var service: CouponCommandServiceImpl

    private fun createCoupon(
        id: Long = 1L,
        status: CouponStatus = CouponStatus.ACTIVE,
        totalQuantity: Int = 100,
        issuedQuantity: Int = 0,
        validFrom: LocalDateTime = LocalDateTime.now().minusDays(1),
        validTo: LocalDateTime = LocalDateTime.now().plusDays(30)
    ): Coupon {
        val coupon = Coupon.create(
            name = "테스트 쿠폰",
            code = "TEST-$id",
            discountType = DiscountType.FIXED_AMOUNT,
            discountValue = BigDecimal("1000"),
            minOrderAmount = BigDecimal("10000"),
            maxDiscountAmount = null,
            totalQuantity = totalQuantity,
            validFrom = validFrom,
            validTo = validTo
        )
        val idField = BaseEntity::class.java.getDeclaredField("id")
        idField.isAccessible = true
        idField.set(coupon, id)
        if (status != CouponStatus.ACTIVE) {
            coupon.changeStatus(status)
        }
        repeat(issuedQuantity) { coupon.issue() }
        return coupon
    }

    private fun createUserCoupon(id: Long = 1L, userId: Long = 10L, couponId: Long = 1L): UserCoupon {
        val userCoupon = UserCoupon.create(userId = userId, couponId = couponId)
        val idField = BaseEntity::class.java.getDeclaredField("id")
        idField.isAccessible = true
        idField.set(userCoupon, id)
        return userCoupon
    }

    @Test
    fun 쿠폰_생성_시_저장된_쿠폰_응답을_반환한다() {
        val request = CouponCreateRequest(
            name = "신규 쿠폰",
            code = "NEW-001",
            discountType = DiscountType.FIXED_AMOUNT,
            discountValue = BigDecimal("2000"),
            minOrderAmount = BigDecimal("20000"),
            maxDiscountAmount = null,
            totalQuantity = 50,
            validFrom = LocalDateTime.now().minusDays(1),
            validTo = LocalDateTime.now().plusDays(7)
        )
        val savedCoupon = createCoupon(id = 1L, totalQuantity = 50)
        whenever(couponRepository.save(any<Coupon>())).thenReturn(savedCoupon)

        val result = service.createCoupon(request)

        assertThat(result.id).isEqualTo(1L)
        verify(couponRepository).save(any<Coupon>())
        verify(couponStockRedisRepository).initializeStock(1L, 50)
    }

    @Test
    fun 쿠폰_발급_성공_시_유저쿠폰_응답을_반환한다() {
        val coupon = createCoupon(id = 1L)
        val userCoupon = createUserCoupon(id = 1L, userId = 10L, couponId = 1L)
        whenever(couponStockRedisRepository.tryReserve(1L, 10L)).thenReturn(CouponReserveResult.Success)
        whenever(couponRepository.findActiveById(1L)).thenReturn(coupon)
        whenever(couponRepository.incrementIssuedQuantity(1L)).thenReturn(1)
        whenever(userCouponRepository.save(any<UserCoupon>())).thenReturn(userCoupon)

        val result = service.issueCoupon(10L, 1L)

        assertThat(result).isNotNull
        verify(couponStockRedisRepository).tryReserve(1L, 10L)
        verify(couponRepository).incrementIssuedQuantity(1L)
        verify(userCouponRepository).save(any<UserCoupon>())
    }

    @Test
    fun 쿠폰_발급_시_재고_소진이면_예외를_던진다() {
        whenever(couponStockRedisRepository.tryReserve(1L, 10L)).thenReturn(CouponReserveResult.Exhausted)

        assertThatThrownBy { service.issueCoupon(10L, 1L) }
            .isInstanceOf(CouponExhaustedException::class.java)
    }

    @Test
    fun 쿠폰_발급_시_이미_발급된_사용자면_예외를_던진다() {
        whenever(couponStockRedisRepository.tryReserve(1L, 10L)).thenReturn(CouponReserveResult.AlreadyIssued)

        assertThatThrownBy { service.issueCoupon(10L, 1L) }
            .isInstanceOf(CouponAlreadyIssuedException::class.java)
    }

    @Test
    fun 쿠폰_발급_시_재고_미초기화면_DB에서_초기화_후_재시도한다() {
        val coupon = createCoupon(id = 1L, totalQuantity = 100, issuedQuantity = 10)
        val userCoupon = createUserCoupon(id = 1L, userId = 10L, couponId = 1L)
        whenever(couponStockRedisRepository.tryReserve(1L, 10L))
            .thenReturn(CouponReserveResult.NotInitialized)
            .thenReturn(CouponReserveResult.Success)
        whenever(couponRepository.findActiveById(1L)).thenReturn(coupon)
        whenever(couponStockRedisRepository.initializeStock(1L, 90)).thenReturn(true)
        whenever(couponRepository.incrementIssuedQuantity(1L)).thenReturn(1)
        whenever(userCouponRepository.save(any<UserCoupon>())).thenReturn(userCoupon)

        service.issueCoupon(10L, 1L)

        verify(couponStockRedisRepository).initializeStock(1L, 90)
        verify(couponStockRedisRepository, times(2)).tryReserve(1L, 10L)
    }

    @Test
    fun 쿠폰_발급_시_DB_저장_실패하면_예외를_던진다() {
        val coupon = createCoupon(id = 1L)
        whenever(couponStockRedisRepository.tryReserve(1L, 10L)).thenReturn(CouponReserveResult.Success)
        whenever(couponRepository.findActiveById(1L)).thenReturn(coupon)
        whenever(couponRepository.incrementIssuedQuantity(1L)).thenReturn(1)
        whenever(userCouponRepository.save(any<UserCoupon>())).thenThrow(RuntimeException("DB error"))

        assertThatThrownBy { service.issueCoupon(10L, 1L) }
            .isInstanceOf(RuntimeException::class.java)
    }

    @Test
    fun 쿠폰_발급_시_DB_재고_소진이면_예외를_던진다() {
        val coupon = createCoupon(id = 1L)
        whenever(couponStockRedisRepository.tryReserve(1L, 10L)).thenReturn(CouponReserveResult.Success)
        whenever(couponRepository.findActiveById(1L)).thenReturn(coupon)
        whenever(couponRepository.incrementIssuedQuantity(1L)).thenReturn(0)

        assertThatThrownBy { service.issueCoupon(10L, 1L) }
            .isInstanceOf(CouponExhaustedException::class.java)
    }

    @Test
    fun 쿠폰_발급_시_만료된_쿠폰이면_예외를_던진다() {
        val expiredCoupon = createCoupon(id = 1L, validTo = LocalDateTime.now().minusDays(1))
        whenever(couponStockRedisRepository.tryReserve(1L, 10L)).thenReturn(CouponReserveResult.Success)
        whenever(couponRepository.findActiveById(1L)).thenReturn(expiredCoupon)

        assertThatThrownBy { service.issueCoupon(10L, 1L) }
            .isInstanceOf(CouponExpiredException::class.java)
    }

    @Test
    fun 쿠폰_발급_시_재시도_후에도_미초기화면_예외를_던진다() {
        val coupon = createCoupon(id = 1L)
        whenever(couponStockRedisRepository.tryReserve(1L, 10L))
            .thenReturn(CouponReserveResult.NotInitialized)
            .thenReturn(CouponReserveResult.NotInitialized)
        whenever(couponRepository.findActiveById(1L)).thenReturn(coupon)
        whenever(couponStockRedisRepository.initializeStock(eq(1L), any())).thenReturn(true)

        assertThatThrownBy { service.issueCoupon(10L, 1L) }
            .isInstanceOf(CouponNotFoundException::class.java)
    }

    @Test
    fun 쿠폰_발급_시_Redis_성공_후_쿠폰_조회_실패하면_예외를_던진다() {
        whenever(couponStockRedisRepository.tryReserve(1L, 10L)).thenReturn(CouponReserveResult.Success)
        whenever(couponRepository.findActiveById(1L)).thenReturn(null)

        assertThatThrownBy { service.issueCoupon(10L, 1L) }
            .isInstanceOf(CouponNotFoundException::class.java)
    }

    @Test
    fun 쿠폰_발급_후_트랜잭션_롤백_시_Redis_재고가_복원된다() {
        val coupon = createCoupon(id = 1L)
        whenever(couponStockRedisRepository.tryReserve(1L, 10L)).thenReturn(CouponReserveResult.Success)
        whenever(couponRepository.findActiveById(1L)).thenReturn(coupon)
        whenever(couponRepository.incrementIssuedQuantity(1L)).thenReturn(0)

        mockStatic(TransactionSynchronizationManager::class.java).use { mocked ->
            mocked.`when`<Boolean> { TransactionSynchronizationManager.isSynchronizationActive() }.thenReturn(true)
            val captor = argumentCaptor<TransactionSynchronization>()

            assertThatThrownBy { service.issueCoupon(10L, 1L) }
                .isInstanceOf(CouponExhaustedException::class.java)

            mocked.verify { TransactionSynchronizationManager.registerSynchronization(captor.capture()) }
            captor.firstValue.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK)
        }

        verify(couponStockRedisRepository).restoreStock(1L, 10L)
    }

    @Test
    fun 롤백_훅에서_Redis_복원이_실패하면_보상_이벤트를_발행한다() {
        val coupon = createCoupon(id = 1L)
        whenever(couponStockRedisRepository.tryReserve(1L, 10L)).thenReturn(CouponReserveResult.Success)
        whenever(couponRepository.findActiveById(1L)).thenReturn(coupon)
        whenever(couponRepository.incrementIssuedQuantity(1L)).thenReturn(0)
        whenever(couponStockRedisRepository.restoreStock(1L, 10L)).thenThrow(RuntimeException("Redis down"))

        mockStatic(TransactionSynchronizationManager::class.java).use { mocked ->
            mocked.`when`<Boolean> { TransactionSynchronizationManager.isSynchronizationActive() }.thenReturn(true)
            val captor = argumentCaptor<TransactionSynchronization>()

            assertThatThrownBy { service.issueCoupon(10L, 1L) }
                .isInstanceOf(CouponExhaustedException::class.java)

            mocked.verify { TransactionSynchronizationManager.registerSynchronization(captor.capture()) }
            captor.firstValue.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK)
        }

        val eventCaptor = argumentCaptor<CouponRestoreEvent>()
        verify(compensationPublisher).publish(eventCaptor.capture())
        assertThat(eventCaptor.firstValue.couponId).isEqualTo(1L)
        assertThat(eventCaptor.firstValue.userId).isEqualTo(10L)
        assertThat(eventCaptor.firstValue.reason).isEqualTo(CouponRestoreReason.DB_INSERT_FAILED)
        assertThat(eventCaptor.firstValue.retryCount).isEqualTo(0)
        verify(compensationMetrics).recordSyncFailure()
    }

    @Test
    fun 롤백_훅에서_동기_보상_성공_시_보상_이벤트는_발행하지_않고_sync_success_만_기록한다() {
        val coupon = createCoupon(id = 1L)
        whenever(couponStockRedisRepository.tryReserve(1L, 10L)).thenReturn(CouponReserveResult.Success)
        whenever(couponRepository.findActiveById(1L)).thenReturn(coupon)
        whenever(couponRepository.incrementIssuedQuantity(1L)).thenReturn(0)

        mockStatic(TransactionSynchronizationManager::class.java).use { mocked ->
            mocked.`when`<Boolean> { TransactionSynchronizationManager.isSynchronizationActive() }.thenReturn(true)
            val captor = argumentCaptor<TransactionSynchronization>()

            assertThatThrownBy { service.issueCoupon(10L, 1L) }
                .isInstanceOf(CouponExhaustedException::class.java)

            mocked.verify { TransactionSynchronizationManager.registerSynchronization(captor.capture()) }
            captor.firstValue.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK)
        }

        verify(couponStockRedisRepository).restoreStock(1L, 10L)
        verify(compensationMetrics).recordSyncSuccess()
        verify(compensationPublisher, never()).publish(any())
    }

    @Test
    fun 보상_큐_발행마저_실패해도_예외를_삼킨다() {
        val coupon = createCoupon(id = 1L)
        whenever(couponStockRedisRepository.tryReserve(1L, 10L)).thenReturn(CouponReserveResult.Success)
        whenever(couponRepository.findActiveById(1L)).thenReturn(coupon)
        whenever(couponRepository.incrementIssuedQuantity(1L)).thenReturn(0)
        whenever(couponStockRedisRepository.restoreStock(1L, 10L)).thenThrow(RuntimeException("Redis down"))
        whenever(compensationPublisher.publish(any())).thenThrow(RuntimeException("Kafka also down"))

        mockStatic(TransactionSynchronizationManager::class.java).use { mocked ->
            mocked.`when`<Boolean> { TransactionSynchronizationManager.isSynchronizationActive() }.thenReturn(true)
            val captor = argumentCaptor<TransactionSynchronization>()

            assertThatThrownBy { service.issueCoupon(10L, 1L) }
                .isInstanceOf(CouponExhaustedException::class.java)

            mocked.verify { TransactionSynchronizationManager.registerSynchronization(captor.capture()) }
            captor.firstValue.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK)
        }

        verify(compensationPublisher).publish(any())
        verify(compensationMetrics).recordSyncFailure()
    }

    @Test
    fun 트랜잭션_커밋_시에는_Redis_재고가_복원되지_않는다() {
        val coupon = createCoupon(id = 1L)
        val userCoupon = createUserCoupon(id = 1L, userId = 10L, couponId = 1L)
        whenever(couponStockRedisRepository.tryReserve(1L, 10L)).thenReturn(CouponReserveResult.Success)
        whenever(couponRepository.findActiveById(1L)).thenReturn(coupon)
        whenever(couponRepository.incrementIssuedQuantity(1L)).thenReturn(1)
        whenever(userCouponRepository.save(any<UserCoupon>())).thenReturn(userCoupon)

        mockStatic(TransactionSynchronizationManager::class.java).use { mocked ->
            mocked.`when`<Boolean> { TransactionSynchronizationManager.isSynchronizationActive() }.thenReturn(true)
            val captor = argumentCaptor<TransactionSynchronization>()

            service.issueCoupon(10L, 1L)

            mocked.verify { TransactionSynchronizationManager.registerSynchronization(captor.capture()) }
            captor.firstValue.afterCompletion(TransactionSynchronization.STATUS_COMMITTED)
        }

        verify(couponStockRedisRepository, never()).restoreStock(any(), any())
    }

    @Test
    fun 트랜잭션_동기화_비활성_시_훅_등록을_건너뛴다() {
        val coupon = createCoupon(id = 1L)
        whenever(couponStockRedisRepository.tryReserve(1L, 10L)).thenReturn(CouponReserveResult.Success)
        whenever(couponRepository.findActiveById(1L)).thenReturn(coupon)
        whenever(couponRepository.incrementIssuedQuantity(1L)).thenReturn(0)

        mockStatic(TransactionSynchronizationManager::class.java).use { mocked ->
            mocked.`when`<Boolean> { TransactionSynchronizationManager.isSynchronizationActive() }.thenReturn(false)

            assertThatThrownBy { service.issueCoupon(10L, 1L) }
                .isInstanceOf(CouponExhaustedException::class.java)

            mocked.verify(
                { TransactionSynchronizationManager.registerSynchronization(any()) },
                org.mockito.kotlin.never()
            )
        }
    }

    @Test
    fun 쿠폰_사용_성공_시_할인_금액을_반환한다() {
        val coupon = createCoupon(id = 1L)
        val userCoupon = createUserCoupon(id = 1L, userId = 10L, couponId = 1L)

        whenever(userCouponRepository.findByUserIdAndCouponId(10L, 1L)).thenReturn(userCoupon)
        whenever(couponRepository.findById(1L)).thenReturn(Optional.of(coupon))
        whenever(userCouponRepository.save(any<UserCoupon>())).thenReturn(userCoupon)

        val result = service.useCoupon(10L, 1L, BigDecimal("30000"), 100L)

        assertThat(result).isEqualByComparingTo("1000")
        assertThat(userCoupon.status).isEqualTo(UserCouponStatus.USED)
        verify(userCouponRepository).save(userCoupon)
    }

    @Test
    fun 쿠폰_사용_시_유저쿠폰이_없으면_예외를_던진다() {
        whenever(userCouponRepository.findByUserIdAndCouponId(10L, 1L)).thenReturn(null)

        assertThatThrownBy { service.useCoupon(10L, 1L, BigDecimal("30000"), 100L) }
            .isInstanceOf(CouponNotFoundException::class.java)
    }

    @Test
    fun 결제_취소_시_사용된_쿠폰을_복원한다() {
        val userCoupon = createUserCoupon(id = 1L, userId = 10L, couponId = 1L)
        userCoupon.use(100L)
        whenever(userCouponRepository.findByUserIdAndCouponId(10L, 1L)).thenReturn(userCoupon)
        whenever(userCouponRepository.save(any<UserCoupon>())).thenReturn(userCoupon)

        service.restoreCouponByPayment(1L, 10L)

        assertThat(userCoupon.status).isEqualTo(UserCouponStatus.ISSUED)
        verify(userCouponRepository).save(userCoupon)
        verifyNoInteractions(couponStockRedisRepository)
    }

    @Test
    fun 결제_취소_시_유저쿠폰이_없으면_아무것도_하지_않는다() {
        whenever(userCouponRepository.findByUserIdAndCouponId(10L, 1L)).thenReturn(null)

        service.restoreCouponByPayment(1L, 10L)

        verify(userCouponRepository).findByUserIdAndCouponId(10L, 1L)
    }

    @Test
    fun 쿠폰_상태_변경_성공() {
        val coupon = createCoupon(id = 1L, status = CouponStatus.ACTIVE)
        whenever(couponRepository.findById(1L)).thenReturn(Optional.of(coupon))
        whenever(couponRepository.save(any<Coupon>())).thenReturn(coupon)

        val result = service.changeCouponStatus(1L, CouponStatus.INACTIVE)

        assertThat(result.id).isEqualTo(1L)
        verify(couponRepository).save(any<Coupon>())
        verify(couponStockRedisRepository).deleteStock(1L)
    }

    @Test
    fun 쿠폰_상태_ACTIVE_변경_시_Redis_재고를_초기화한다() {
        val coupon = createCoupon(id = 1L, status = CouponStatus.INACTIVE, totalQuantity = 100, issuedQuantity = 20)
        whenever(couponRepository.findById(1L)).thenReturn(Optional.of(coupon))
        whenever(couponRepository.save(any<Coupon>())).thenReturn(coupon)

        service.changeCouponStatus(1L, CouponStatus.ACTIVE)

        verify(couponStockRedisRepository).initializeStock(1L, 80)
    }

    @Test
    fun 쿠폰_상태_변경_시_쿠폰_없으면_예외() {
        whenever(couponRepository.findById(99L)).thenReturn(Optional.empty())

        assertThatThrownBy { service.changeCouponStatus(99L, CouponStatus.INACTIVE) }
            .isInstanceOf(CouponNotFoundException::class.java)
    }

    @Test
    fun 주문_취소_시_사용된_쿠폰을_복원한다() {
        val userCoupon = createUserCoupon(id = 1L, userId = 10L, couponId = 1L)
        userCoupon.use(100L)
        whenever(userCouponRepository.findByOrderId(100L)).thenReturn(userCoupon)
        whenever(userCouponRepository.save(any<UserCoupon>())).thenReturn(userCoupon)

        service.restoreCouponByOrder(100L)

        assertThat(userCoupon.status).isEqualTo(UserCouponStatus.ISSUED)
        verify(userCouponRepository).save(userCoupon)
        verifyNoInteractions(couponStockRedisRepository)
    }

    @Test
    fun 주문_취소_시_유저쿠폰이_없으면_아무것도_하지_않는다() {
        whenever(userCouponRepository.findByOrderId(100L)).thenReturn(null)

        service.restoreCouponByOrder(100L)

        verify(userCouponRepository).findByOrderId(100L)
    }

    @Test
    fun 주문_취소_시_이미_복원된_쿠폰은_저장하지_않는다() {
        val userCoupon = createUserCoupon(id = 1L, userId = 10L, couponId = 1L)
        whenever(userCouponRepository.findByOrderId(100L)).thenReturn(userCoupon)

        service.restoreCouponByOrder(100L)

        verify(userCouponRepository).findByOrderId(100L)
    }

    @Test
    fun 쿠폰_사용_시_유저쿠폰_상태가_ISSUED가_아니면_예외() {
        val userCoupon = createUserCoupon(id = 1L, userId = 10L, couponId = 1L)
        userCoupon.use(100L)
        whenever(userCouponRepository.findByUserIdAndCouponId(10L, 1L)).thenReturn(userCoupon)

        assertThatThrownBy { service.useCoupon(10L, 1L, BigDecimal("30000"), 100L) }
            .isInstanceOf(CouponNotAvailableException::class.java)
    }

    @Test
    fun 쿠폰_사용_시_최소_주문금액_미달이면_예외() {
        val coupon = createCoupon(id = 1L)
        val userCoupon = createUserCoupon(id = 1L, userId = 10L, couponId = 1L)
        whenever(userCouponRepository.findByUserIdAndCouponId(10L, 1L)).thenReturn(userCoupon)
        whenever(couponRepository.findById(1L)).thenReturn(Optional.of(coupon))

        assertThatThrownBy { service.useCoupon(10L, 1L, BigDecimal("5000"), 100L) }
            .isInstanceOf(CouponMinAmountNotMetException::class.java)
    }

    @Test
    fun 결제_취소_시_ISSUED_상태_쿠폰은_저장하지_않는다() {
        val userCoupon = createUserCoupon(id = 1L, userId = 10L, couponId = 1L)
        whenever(userCouponRepository.findByUserIdAndCouponId(10L, 1L)).thenReturn(userCoupon)

        service.restoreCouponByPayment(1L, 10L)

        verify(userCouponRepository).findByUserIdAndCouponId(10L, 1L)
    }

    @Test
    fun 쿠폰_사용_시_쿠폰이_존재하지_않으면_예외를_던진다() {
        val userCoupon = createUserCoupon(id = 1L, userId = 10L, couponId = 1L)
        whenever(userCouponRepository.findByUserIdAndCouponId(10L, 1L)).thenReturn(userCoupon)
        whenever(couponRepository.findById(1L)).thenReturn(Optional.empty())

        assertThatThrownBy { service.useCoupon(10L, 1L, BigDecimal("30000"), 100L) }
            .isInstanceOf(CouponNotFoundException::class.java)
    }
}
