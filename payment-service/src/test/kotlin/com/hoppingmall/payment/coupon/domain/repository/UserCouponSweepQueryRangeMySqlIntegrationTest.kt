package com.hoppingmall.payment.coupon.domain.repository

import com.hoppingmall.payment.support.MySqlJpaTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.sql.Timestamp
import java.time.LocalDateTime

@DisplayName("쿠폰 정합성 스윕의 발급 사용자 조회 읽는 범위 MySQL 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class UserCouponSweepQueryRangeMySqlIntegrationTest : MySqlJpaTestSupport() {

    @Autowired
    private lateinit var userCouponRepository: UserCouponRepository

    @BeforeEach
    fun seedUserCoupons() {
        val createdAt = Timestamp.valueOf(LocalDateTime.now().minusDays(1))
        val otherCoupons = (0 until OTHER_ROWS).map { index ->
            arrayOf<Any>(USER_BASE + index, OTHER_COUPON_BASE + index % OTHER_COUPONS, "ISSUED", createdAt, createdAt)
        }
        val targetCoupon = TARGET_USER_IDS.map { userId ->
            arrayOf<Any>(userId, TARGET_COUPON_ID, "ISSUED", createdAt, createdAt)
        }
        jdbcTemplate.batchUpdate(
            "INSERT INTO user_coupons (user_id, coupon_id, status, created_at, updated_at) VALUES (?, ?, ?, ?, ?)",
            otherCoupons + targetCoupon
        )
        jdbcTemplate.queryForList("ANALYZE TABLE user_coupons")
    }

    @Test
    fun 쿠폰별_발급_사용자_조회는_다른_쿠폰의_발급_기록을_읽지_않는다() {
        val plan = jdbcTemplate.queryForList(
            "EXPLAIN SELECT uc.user_id FROM user_coupons uc WHERE uc.coupon_id = ?",
            TARGET_COUPON_ID
        )
        val estimatedRows = plan.sumOf { (it["rows"] as Number?)?.toLong() ?: 0L }

        assertThat(userCouponRepository.findUserIdsByCouponId(TARGET_COUPON_ID))
            .containsExactlyInAnyOrderElementsOf(TARGET_USER_IDS)
        assertThat(estimatedRows)
            .withFailMessage { "쿠폰별 발급 사용자 조회가 다른 쿠폰의 발급 기록까지 읽는다(예상 읽기 ${estimatedRows}행, 다른 쿠폰 행 ${OTHER_ROWS}개): $plan" }
            .isLessThan(TARGET_READ_LIMIT)
    }

    companion object {
        private const val OTHER_ROWS = 2_000
        private const val OTHER_COUPONS = 20
        private const val OTHER_COUPON_BASE = 88_100L
        private const val USER_BASE = 880_000L
        private const val TARGET_COUPON_ID = 88_000L
        private const val TARGET_READ_LIMIT = 100L
        private val TARGET_USER_IDS = listOf(880_001L, 880_002L, 990_003L, 990_004L, 990_005L)
    }
}
