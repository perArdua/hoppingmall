package com.hoppingmall.payment.coupon.domain.repository

import com.hoppingmall.payment.support.MySqlJpaTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.PageRequest
import java.sql.Timestamp
import java.time.LocalDateTime

@DisplayName("user_coupons의 user_id 인덱스가 유니크 키 하나뿐인지 MySQL 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class UserCouponUserIdIndexMySqlIntegrationTest : MySqlJpaTestSupport() {

    @Autowired
    private lateinit var userCouponRepository: UserCouponRepository

    private fun indexesStartingWithUserId(): List<Map<String, Any>> =
        jdbcTemplate.queryForList(
            """
            SELECT INDEX_NAME, NON_UNIQUE FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_coupons'
            AND SEQ_IN_INDEX = 1 AND COLUMN_NAME = 'user_id'
            """.trimIndent()
        )

    @Test
    fun user_id로_시작하는_인덱스는_user_id_coupon_id_유니크_키_하나뿐이다() {
        val indexes = indexesStartingWithUserId()

        assertThat(indexes)
            .withFailMessage { "user_id로 시작하는 인덱스가 유니크 키와 겹친다: $indexes" }
            .hasSize(1)
        assertThat((indexes[0]["NON_UNIQUE"] as Number).toInt()).isEqualTo(0)
    }

    @Test
    fun 사용자별_쿠폰_조회는_유니크_키로_그_사용자의_행만_읽는다() {
        val createdAt = Timestamp.valueOf(LocalDateTime.now().minusDays(1))
        val otherUsers = (0 until OTHER_ROWS).map { index ->
            arrayOf<Any>(USER_BASE + index % OTHER_USERS, COUPON_BASE + index, "ISSUED", createdAt, createdAt)
        }
        val targetUser = (0 until TARGET_ROWS).map { index ->
            arrayOf<Any>(TARGET_USER_ID, TARGET_COUPON_BASE + index, "ISSUED", createdAt, createdAt)
        }
        jdbcTemplate.batchUpdate(
            "INSERT INTO user_coupons (user_id, coupon_id, status, created_at, updated_at) VALUES (?, ?, ?, ?, ?)",
            otherUsers + targetUser
        )
        jdbcTemplate.queryForList("ANALYZE TABLE user_coupons")

        val plan = jdbcTemplate.queryForList("EXPLAIN SELECT uc.id FROM user_coupons uc WHERE uc.user_id = ?", TARGET_USER_ID)
        val uniqueKey = indexesStartingWithUserId().single { (it["NON_UNIQUE"] as Number).toInt() == 0 }["INDEX_NAME"]
        val estimatedRows = plan.sumOf { (it["rows"] as Number?)?.toLong() ?: 0L }

        assertThat(userCouponRepository.findByUserId(TARGET_USER_ID, PageRequest.of(0, 20)).content)
            .hasSize(TARGET_ROWS)
        assertThat(plan.single()["key"]).isEqualTo(uniqueKey)
        assertThat(estimatedRows)
            .withFailMessage { "사용자별 쿠폰 조회가 다른 사용자의 행까지 읽는다(예상 읽기 ${estimatedRows}행): $plan" }
            .isLessThan(TARGET_READ_LIMIT)
    }

    companion object {
        private const val OTHER_ROWS = 2_000
        private const val OTHER_USERS = 200
        private const val USER_BASE = 757_000L
        private const val COUPON_BASE = 758_000L
        private const val TARGET_USER_ID = 756_000L
        private const val TARGET_COUPON_BASE = 759_000L
        private const val TARGET_ROWS = 3
        private const val TARGET_READ_LIMIT = 100L
    }
}
