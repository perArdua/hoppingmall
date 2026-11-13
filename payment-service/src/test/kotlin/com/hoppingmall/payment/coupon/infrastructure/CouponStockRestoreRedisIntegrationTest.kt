package com.hoppingmall.payment.coupon.infrastructure

import com.hoppingmall.payment.coupon.domain.Coupon
import com.hoppingmall.payment.coupon.domain.repository.CouponRepository
import com.hoppingmall.payment.coupon.domain.repository.UserCouponRepository
import com.hoppingmall.payment.coupon.enum.CouponStatus
import com.hoppingmall.payment.coupon.enum.DiscountType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.redisson.Redisson
import org.redisson.api.RedissonClient
import org.redisson.client.codec.StringCodec
import org.redisson.config.Config
import org.springframework.test.util.ReflectionTestUtils
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName
import java.math.BigDecimal
import java.time.LocalDateTime

@DisplayName("쿠폰 Redis 재고 복원 스크립트 (실제 Redis)")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CouponStockRestoreRedisIntegrationTest {

    private lateinit var redisson: RedissonClient
    private lateinit var repository: CouponStockRedisRepository

    private val couponId = 1L

    @BeforeAll
    fun startClient() {
        val config = Config()
        config.useSingleServer().address = "redis://${redis.host}:${redis.getMappedPort(REDIS_PORT)}"
        redisson = Redisson.create(config)
        repository = CouponStockRedisRepository(redisson)
    }

    @AfterAll
    fun stopClient() {
        redisson.shutdown()
    }

    @BeforeEach
    fun clearRedis() {
        redisson.keys.flushall()
        repository.initializeStock(couponId, 5)
    }

    @Test
    fun 두_인스턴스의_스윕이_같은_고스트를_함께_보정해도_재고는_한_번만_복원된다() {
        assertThat(repository.tryReserve(couponId, 10L)).isEqualTo(CouponReserveResult.Success)
        assertThat(stock()).isEqualTo(4)
        backdateReservation(10L)

        val schedulerB = scheduler(mock<UserCouponRepository>().also {
            whenever(it.findUserIdsByCouponId(couponId)).thenReturn(emptyList())
        })
        val schedulerA = scheduler(mock<UserCouponRepository>().also {
            whenever(it.findUserIdsByCouponId(couponId)).thenAnswer {
                schedulerB.reconcile()
                emptyList<Long>()
            }
        })

        schedulerA.reconcile()

        assertThat(stock()).isEqualTo(5)
        assertThat(repository.getIssuedUserIds(couponId)).isEmpty()
    }

    @Test
    fun 같은_고스트의_복원을_두_번_불러도_재고는_한_번만_오른다() {
        repository.tryReserve(couponId, 10L)

        repository.restoreStock(couponId, 10L)
        repository.restoreStock(couponId, 10L)

        assertThat(stock()).isEqualTo(5)
        assertThat(repository.getIssuedUserIds(couponId)).isEmpty()
    }

    @Test
    fun 발급_집합에_없는_사용자의_복원은_재고를_올리지_않는다() {
        repository.restoreStock(couponId, 99L)

        assertThat(stock()).isEqualTo(5)
    }

    @Test
    fun 예약한_사용자의_복원은_재고를_되돌리고_다시_예약할_수_있게_한다() {
        repository.tryReserve(couponId, 10L)

        repository.restoreStock(couponId, 10L)

        assertThat(stock()).isEqualTo(5)
        assertThat(repository.tryReserve(couponId, 10L)).isEqualTo(CouponReserveResult.Success)
        assertThat(stock()).isEqualTo(4)
    }

    private fun scheduler(userCouponRepository: UserCouponRepository): CouponStockReconciliationScheduler {
        val couponRepository = mock<CouponRepository>()
        whenever(couponRepository.findAllActive()).thenReturn(listOf(activeCoupon()))
        return CouponStockReconciliationScheduler(couponRepository, userCouponRepository, repository)
    }

    private fun backdateReservation(userId: Long) {
        redisson.getMap<String, String>("coupon:{$couponId}:reserved-at", StringCodec.INSTANCE)[userId.toString()] = "0"
    }

    private fun stock(): Int =
        redisson.getBucket<String>("coupon:{$couponId}:stock", StringCodec.INSTANCE).get().toInt()

    private fun activeCoupon(): Coupon {
        val coupon = Coupon.create(
            name = "쿠폰",
            code = "C$couponId",
            discountType = DiscountType.FIXED_AMOUNT,
            discountValue = BigDecimal("1000"),
            minOrderAmount = BigDecimal("10000"),
            maxDiscountAmount = BigDecimal("1000"),
            totalQuantity = 5,
            validFrom = LocalDateTime.now().minusDays(1),
            validTo = LocalDateTime.now().plusDays(7)
        )
        coupon.changeStatus(CouponStatus.ACTIVE)
        ReflectionTestUtils.setField(coupon, "id", couponId)
        return coupon
    }

    companion object {
        private const val REDIS_PORT = 6379

        private val redis: GenericContainer<*> by lazy {
            GenericContainer(DockerImageName.parse("redis:7.4"))
                .withExposedPorts(REDIS_PORT)
                .also { it.start() }
        }
    }
}
