package com.hoppingmall.payment.coupon.service

import com.hoppingmall.payment.coupon.domain.repository.CouponRepository
import com.hoppingmall.payment.coupon.domain.repository.UserCouponRepository
import com.hoppingmall.payment.coupon.dto.request.CouponCreateRequest
import com.hoppingmall.payment.coupon.enum.CouponStatus
import com.hoppingmall.payment.coupon.enum.DiscountType
import com.hoppingmall.payment.coupon.exception.CouponAlreadyIssuedException
import com.hoppingmall.payment.coupon.infrastructure.CouponStockRedisRepository
import com.hoppingmall.payment.support.MySqlJpaTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.redisson.Redisson
import org.redisson.api.RedissonClient
import org.redisson.client.codec.StringCodec
import org.redisson.config.Config
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName
import java.math.BigDecimal
import java.time.LocalDateTime

@DisplayName("Redis 발급 상태가 지워진 뒤 이미 받은 사용자의 재발급 (실제 MySQL·Redis)")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class CouponReissueAfterRedisResetMySqlIntegrationTest : MySqlJpaTestSupport() {

    @Autowired
    private lateinit var couponRepository: CouponRepository

    @Autowired
    private lateinit var userCouponRepository: UserCouponRepository

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    private lateinit var redisson: RedissonClient

    private lateinit var transactionTemplate: TransactionTemplate

    private lateinit var service: CouponCommandServiceImpl

    @BeforeEach
    fun setUp() {
        val config = Config()
        config.useSingleServer().address = "redis://${redis.host}:${redis.getMappedPort(REDIS_PORT)}"
        redisson = Redisson.create(config)
        redisson.keys.flushall()
        transactionTemplate = TransactionTemplate(transactionManager)
        service = CouponCommandServiceImpl(
            couponRepository,
            userCouponRepository,
            CouponStockRedisRepository(redisson),
            mock(),
            mock()
        )
    }

    @AfterEach
    fun tearDown() {
        redisson.shutdown()
    }

    @Test
    fun 쿠폰을_껐다_켠_뒤_이미_받은_사용자가_다시_받으면_이미_받음으로_거절하고_발급_수는_그대로다() {
        val couponId = createdCouponId()
        inTx { service.issueCoupon(USER_ID, couponId) }
        inTx { service.changeCouponStatus(couponId, CouponStatus.INACTIVE) }
        inTx { service.changeCouponStatus(couponId, CouponStatus.ACTIVE) }

        val thrown = catchThrowable { inTx { service.issueCoupon(USER_ID, couponId) } }

        assertThat(thrown).isInstanceOf(CouponAlreadyIssuedException::class.java)
        assertThat(issuedQuantity(couponId)).isEqualTo(1)
        assertThat(userCouponCount(couponId)).isEqualTo(1)
        assertThat(stock(couponId)).isEqualTo(TOTAL - 1)
        assertThat(issuedSet(couponId)).containsExactly(USER_ID.toString())
    }

    @Test
    fun 쿠폰을_껐다_켠_뒤_같은_사용자가_여러_번_눌러도_매번_이미_받음이다() {
        val couponId = createdCouponId()
        inTx { service.issueCoupon(USER_ID, couponId) }
        inTx { service.changeCouponStatus(couponId, CouponStatus.INACTIVE) }
        inTx { service.changeCouponStatus(couponId, CouponStatus.ACTIVE) }

        val results = (1..3).map { catchThrowable { inTx { service.issueCoupon(USER_ID, couponId) } } }

        assertThat(results).allSatisfy { assertThat(it).isInstanceOf(CouponAlreadyIssuedException::class.java) }
        assertThat(stock(couponId)).isEqualTo(TOTAL - 1)
        assertThat(issuedQuantity(couponId)).isEqualTo(1)
    }

    @Test
    fun 쿠폰을_껐다_켠_뒤_받지_않은_사용자는_받고_재고는_DB_발급_수와_맞다() {
        val couponId = createdCouponId()
        inTx { service.issueCoupon(USER_ID, couponId) }
        inTx { service.changeCouponStatus(couponId, CouponStatus.INACTIVE) }
        inTx { service.changeCouponStatus(couponId, CouponStatus.ACTIVE) }

        inTx { service.issueCoupon(OTHER_USER_ID, couponId) }

        assertThat(issuedQuantity(couponId)).isEqualTo(2)
        assertThat(stock(couponId)).isEqualTo(TOTAL - 2)
        assertThat(issuedSet(couponId)).containsExactlyInAnyOrder(USER_ID.toString(), OTHER_USER_ID.toString())
    }

    @Test
    fun Redis_키가_모두_사라진_뒤_이미_받은_사용자가_다시_받으면_이미_받음으로_거절한다() {
        val couponId = createdCouponId()
        inTx { service.issueCoupon(USER_ID, couponId) }
        redisson.keys.flushall()

        val thrown = catchThrowable { inTx { service.issueCoupon(USER_ID, couponId) } }

        assertThat(thrown).isInstanceOf(CouponAlreadyIssuedException::class.java)
        assertThat(issuedQuantity(couponId)).isEqualTo(1)
        assertThat(stock(couponId)).isEqualTo(TOTAL - 1)
        assertThat(issuedSet(couponId)).containsExactly(USER_ID.toString())
    }

    @Test
    fun 받은_사용자_집합만_사라진_뒤_이미_받은_사용자가_다시_받으면_이미_받음이고_재고는_줄지_않는다() {
        val couponId = createdCouponId()
        inTx { service.issueCoupon(USER_ID, couponId) }
        redisson.keys.delete("coupon:{$couponId}:issued")

        val thrown = catchThrowable { inTx { service.issueCoupon(USER_ID, couponId) } }
        inTx { service.issueCoupon(OTHER_USER_ID, couponId) }

        assertThat(thrown).isInstanceOf(CouponAlreadyIssuedException::class.java)
        assertThat(issuedQuantity(couponId)).isEqualTo(2)
        assertThat(stock(couponId)).isEqualTo(TOTAL - 2)
        assertThat(issuedSet(couponId)).containsExactlyInAnyOrder(USER_ID.toString(), OTHER_USER_ID.toString())
    }

    private fun createdCouponId(): Long = inTx {
        service.createCoupon(
            CouponCreateRequest(
                name = "재발급 쿠폰",
                code = "REISSUE-${System.nanoTime()}",
                discountType = DiscountType.FIXED_AMOUNT,
                discountValue = BigDecimal("1000"),
                minOrderAmount = BigDecimal("10000"),
                maxDiscountAmount = null,
                totalQuantity = TOTAL,
                validFrom = LocalDateTime.now().minusDays(1),
                validTo = LocalDateTime.now().plusDays(7)
            )
        ).id
    }

    private fun <T> inTx(action: () -> T): T = transactionTemplate.execute { action() }!!

    private fun issuedQuantity(couponId: Long): Int =
        jdbcTemplate.queryForObject("SELECT issued_quantity FROM coupons WHERE id = ?", Int::class.java, couponId)!!

    private fun userCouponCount(couponId: Long): Int =
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user_coupons WHERE coupon_id = ?", Int::class.java, couponId)!!

    private fun stock(couponId: Long): Int =
        redisson.getBucket<String>("coupon:{$couponId}:stock", StringCodec.INSTANCE).get().toInt()

    private fun issuedSet(couponId: Long): Set<String> =
        redisson.getSet<String>("coupon:{$couponId}:issued", StringCodec.INSTANCE).readAll()

    companion object {
        private const val REDIS_PORT = 6379
        private const val TOTAL = 5
        private const val USER_ID = 10L
        private const val OTHER_USER_ID = 20L

        private val redis: GenericContainer<*> by lazy {
            GenericContainer(DockerImageName.parse("redis:7.4"))
                .withExposedPorts(REDIS_PORT)
                .also { it.start() }
        }
    }
}
