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

@DisplayName("쿠폰 예약 시각 해시는 발급 확정·복원·비활성화 때 지우고, 스윕 판단은 그대로 (실제 Redis)")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CouponReservedAtCleanupRedisIntegrationTest {

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
    fun 발급이_커밋되면_그_사용자의_예약_시각을_지운다() {
        repository.tryReserve(couponId, 10L)
        repository.tryReserve(couponId, 20L)

        repository.clearReservedAt(couponId, 10L)

        assertThat(reservedAt(10L)).isNull()
        assertThat(reservedAt(20L)).isNotNull()
        assertThat(repository.getIssuedUserIds(couponId)).containsExactlyInAnyOrder(10L, 20L)
    }

    @Test
    fun 롤백으로_재고를_복원하면_예약_시각도_지운다() {
        repository.tryReserve(couponId, 10L)

        repository.restoreStock(couponId, 10L)

        assertThat(reservedAt(10L)).isNull()
        assertThat(stock()).isEqualTo(5)
    }

    @Test
    fun 보상_큐가_재고를_복원하면_예약_시각도_지운다() {
        repository.tryReserve(couponId, 10L)

        repository.restoreStockIdempotent(couponId, 10L)

        assertThat(reservedAt(10L)).isNull()
        assertThat(stock()).isEqualTo(5)
    }

    @Test
    fun 스윕이_버려진_발급을_복원하면_예약_시각도_지운다() {
        repository.tryReserve(couponId, 10L)
        backdateReservation(10L)

        sweepWithEmptyDb()

        assertThat(reservedAt(10L)).isNull()
        assertThat(stock()).isEqualTo(5)
    }

    @Test
    fun 쿠폰을_비활성화하면_예약_시각_해시를_지운다() {
        repository.tryReserve(couponId, 10L)
        repository.tryReserve(couponId, 20L)

        repository.deleteStock(couponId)

        assertThat(redisson.keys.countExists(RESERVED_AT_KEY)).isZero()
    }

    @Test
    fun 비활성화_뒤_재활성화하고_다시_예약한_사용자는_새_예약_시각으로_판단해_복원하지_않는다() {
        repository.tryReserve(couponId, 10L)
        backdateReservation(10L)
        repository.deleteStock(couponId)
        repository.initializeStock(couponId, 5)
        repository.tryReserve(couponId, 10L)

        sweepWithEmptyDb()

        assertThat(stock()).isEqualTo(4)
        assertThat(repository.getIssuedUserIds(couponId)).containsExactly(10L)
    }

    @Test
    fun 비활성화_뒤_다시_예약하지_않은_사용자는_스윕_후보가_아니다() {
        repository.tryReserve(couponId, 10L)
        backdateReservation(10L)
        repository.deleteStock(couponId)
        repository.initializeStock(couponId, 5)
        repository.tryReserve(couponId, 20L)

        sweepWithEmptyDb()

        assertThat(stock()).isEqualTo(4)
        assertThat(repository.getIssuedUserIds(couponId)).containsExactly(20L)
    }

    @Test
    fun 재활성화_뒤_예약이_최소_나이를_넘으면_스윕이_복원한다() {
        repository.tryReserve(couponId, 10L)
        repository.deleteStock(couponId)
        repository.initializeStock(couponId, 5)
        repository.tryReserve(couponId, 10L)
        backdateReservation(10L)

        sweepWithEmptyDb()

        assertThat(stock()).isEqualTo(5)
        assertThat(repository.getIssuedUserIds(couponId)).isEmpty()
    }

    @Test
    fun 커밋으로_예약_시각이_지워진_사용자를_커밋_전_DB_조회로_후보로_봐도_복원하지_않는다() {
        repository.tryReserve(couponId, 10L)
        repository.clearReservedAt(couponId, 10L)

        sweepWithEmptyDb()

        assertThat(stock()).isEqualTo(4)
        assertThat(repository.getIssuedUserIds(couponId)).containsExactly(10L)
    }

    private fun sweepWithEmptyDb() {
        val couponRepository = mock<CouponRepository>()
        whenever(couponRepository.findAllActive()).thenReturn(listOf(activeCoupon()))
        val userCouponRepository = mock<UserCouponRepository>()
        whenever(userCouponRepository.findUserIdsByCouponId(couponId)).thenReturn(emptyList())
        CouponStockReconciliationScheduler(couponRepository, userCouponRepository, repository).reconcile()
    }

    private fun backdateReservation(userId: Long) {
        reservedAtMap()[userId.toString()] = (redisMillis() - MIN_RESERVATION_AGE_MS - 1_000L).toString()
    }

    private fun reservedAt(userId: Long): String? = reservedAtMap()[userId.toString()]

    private fun reservedAtMap() = redisson.getMap<String, String>(RESERVED_AT_KEY, StringCodec.INSTANCE)

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
        private const val RESERVED_AT_KEY = "coupon:{1}:reserved-at"

        private val redis: GenericContainer<*> by lazy {
            GenericContainer(DockerImageName.parse("redis:7.4"))
                .withExposedPorts(REDIS_PORT)
                .also { it.start() }
        }
    }
}
