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
import org.redisson.api.RScript
import org.redisson.api.RedissonClient
import org.redisson.client.codec.StringCodec
import org.redisson.config.Config
import org.springframework.test.util.ReflectionTestUtils
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName
import java.math.BigDecimal
import java.time.LocalDateTime

@DisplayName("쿠폰 정합성 스윕이 예약 시각을 읽은 뒤 복원하기 전에 바뀐 예약은 복원하지 않음 (실제 Redis)")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CouponReconciliationRestoreRaceRedisIntegrationTest {

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
    fun 스윕이_버려진_발급으로_고른_뒤_보상_큐가_복원하고_다시_예약한_사용자의_새_예약은_복원하지_않는다() {
        repository.tryReserve(couponId, 10L)
        backdateReservation(10L)
        val racing = racingRepository {
            repository.restoreStockIdempotent(couponId, 10L)
            repository.tryReserve(couponId, 10L)
        }

        sweepWithEmptyDb(racing)

        assertThat(stock()).isEqualTo(4)
        assertThat(repository.getIssuedUserIds(couponId)).containsExactly(10L)
        assertThat(reservedAt(10L)).isNotNull()
    }

    @Test
    fun 스윕이_버려진_발급으로_고른_뒤_롤백_복원되고_다시_예약해_커밋한_사용자는_복원하지_않는다() {
        repository.tryReserve(couponId, 10L)
        backdateReservation(10L)
        val racing = racingRepository {
            repository.restoreStock(couponId, 10L)
            repository.tryReserve(couponId, 10L)
            repository.clearReservedAt(couponId, 10L)
        }

        sweepWithEmptyDb(racing)

        assertThat(stock()).isEqualTo(4)
        assertThat(repository.getIssuedUserIds(couponId)).containsExactly(10L)
    }

    @Test
    fun 스윕이_고른_뒤_예약이_그대로인_버려진_발급은_복원한다() {
        repository.tryReserve(couponId, 10L)
        repository.tryReserve(couponId, 20L)
        backdateReservation(10L)
        val racing = racingRepository { }

        sweepWithEmptyDb(racing)

        assertThat(stock()).isEqualTo(4)
        assertThat(repository.getIssuedUserIds(couponId)).containsExactly(20L)
    }

    @Test
    fun 스윕이_고른_뒤_다른_경로가_먼저_복원한_사용자는_재고를_한_번_더_올리지_않는다() {
        repository.tryReserve(couponId, 10L)
        backdateReservation(10L)
        val racing = racingRepository {
            repository.restoreStockIdempotent(couponId, 10L)
        }

        sweepWithEmptyDb(racing)

        assertThat(stock()).isEqualTo(5)
        assertThat(repository.getIssuedUserIds(couponId)).isEmpty()
    }

    private fun racingRepository(betweenCheckAndRestore: () -> Unit): CouponStockRedisRepository =
        object : CouponStockRedisRepository(redisson) {
            override fun findReservedAfter(couponId: Long, userIds: Collection<Long>, reservedAfterMillis: Long): Set<Long> {
                val recent = super.findReservedAfter(couponId, userIds, reservedAfterMillis)
                betweenCheckAndRestore()
                return recent
            }
        }

    private fun sweepWithEmptyDb(redisRepository: CouponStockRedisRepository) {
        val couponRepository = mock<CouponRepository>()
        whenever(couponRepository.findAllActive()).thenReturn(listOf(activeCoupon()))
        val userCouponRepository = mock<UserCouponRepository>()
        whenever(userCouponRepository.findUserIdsByCouponId(couponId)).thenReturn(emptyList())
        CouponStockReconciliationScheduler(couponRepository, userCouponRepository, redisRepository).reconcile()
    }

    private fun backdateReservation(userId: Long) {
        reservedAtMap()[userId.toString()] = (redisMillis() - MIN_RESERVATION_AGE_MS - 1_000L).toString()
    }

    private fun reservedAt(userId: Long): String? = reservedAtMap()[userId.toString()]

    private fun reservedAtMap() =
        redisson.getMap<String, String>("coupon:{$couponId}:reserved-at", StringCodec.INSTANCE)

    private fun redisMillis(): Long = redisson.getScript(StringCodec.INSTANCE).eval(
        RScript.Mode.READ_ONLY,
        "local t = redis.call('TIME') return t[1] * 1000 + math.floor(t[2] / 1000)",
        RScript.ReturnType.LONG
    )

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
        private const val MIN_RESERVATION_AGE_MS = 300_000L

        private val redis: GenericContainer<*> by lazy {
            GenericContainer(DockerImageName.parse("redis:7.4"))
                .withExposedPorts(REDIS_PORT)
                .also { it.start() }
        }
    }
}
