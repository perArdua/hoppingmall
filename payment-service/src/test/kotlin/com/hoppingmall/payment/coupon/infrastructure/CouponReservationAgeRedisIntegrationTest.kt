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

@DisplayName("쿠폰 정합성 스윕은 발급 트랜잭션 제한 시간보다 오래된 예약만, 보상 큐는 발급 집합에 있을 때만 재고를 복원 (실제 Redis)")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CouponReservationAgeRedisIntegrationTest {

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
    fun 예약했지만_DB_커밋_전인_발급은_스윕이_복원하지_않는다() {
        repository.tryReserve(couponId, 10L)

        sweepWithEmptyDb()

        assertThat(stock()).isEqualTo(4)
        assertThat(repository.getIssuedUserIds(couponId)).containsExactly(10L)
    }

    @Test
    fun 발급_트랜잭션_제한_시간보다_오래된_예약만_버려진_발급으로_보고_복원한다() {
        repository.tryReserve(couponId, 10L)
        repository.tryReserve(couponId, 20L)
        backdateReservation(10L)

        sweepWithEmptyDb()

        assertThat(stock()).isEqualTo(4)
        assertThat(repository.getIssuedUserIds(couponId)).containsExactly(20L)
    }

    @Test
    fun 예약_시각이_없는_예전_예약은_스윕이_처음_본_시각부터_최소_나이가_지나야_복원한다() {
        legacyReservation(30L)

        sweepWithEmptyDb()

        assertThat(stock()).isEqualTo(4)
        assertThat(reservedAt(30L)).isNotNull()

        backdateReservation(30L)
        sweepWithEmptyDb()

        assertThat(stock()).isEqualTo(5)
        assertThat(repository.getIssuedUserIds(couponId)).isEmpty()
    }

    @Test
    fun 버려진_발급이_복원된_뒤_다시_예약한_사용자는_새_예약_시각으로_판단한다() {
        repository.tryReserve(couponId, 10L)
        backdateReservation(10L)
        repository.restoreStock(couponId, 10L)
        repository.tryReserve(couponId, 10L)

        sweepWithEmptyDb()

        assertThat(stock()).isEqualTo(4)
        assertThat(repository.getIssuedUserIds(couponId)).containsExactly(10L)
    }

    @Test
    fun 스윕이_먼저_복원한_버려진_발급을_보상_큐가_다시_복원해도_재고는_한_번만_오른다() {
        repository.tryReserve(couponId, 10L)
        backdateReservation(10L)
        sweepWithEmptyDb()
        assertThat(stock()).isEqualTo(5)

        repository.restoreStockIdempotent(couponId, 10L)

        assertThat(stock()).isEqualTo(5)
        assertThat(repository.getIssuedUserIds(couponId)).isEmpty()
    }

    @Test
    fun 보상_큐는_발급_집합에_있는_사용자의_재고를_한_번만_되돌린다() {
        repository.tryReserve(couponId, 10L)

        repository.restoreStockIdempotent(couponId, 10L)
        repository.restoreStockIdempotent(couponId, 10L)

        assertThat(stock()).isEqualTo(5)
        assertThat(repository.getIssuedUserIds(couponId)).isEmpty()
    }

    private fun sweepWithEmptyDb() {
        val couponRepository = mock<CouponRepository>()
        whenever(couponRepository.findAllActive()).thenReturn(listOf(activeCoupon()))
        val userCouponRepository = mock<UserCouponRepository>()
        whenever(userCouponRepository.findUserIdsByCouponId(couponId)).thenReturn(emptyList())
        CouponStockReconciliationScheduler(couponRepository, userCouponRepository, repository).reconcile()
    }

    private fun legacyReservation(userId: Long) {
        redisson.getBucket<String>("coupon:{$couponId}:stock", StringCodec.INSTANCE).set("4")
        redisson.getSet<String>("coupon:{$couponId}:issued", StringCodec.INSTANCE).add(userId.toString())
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
