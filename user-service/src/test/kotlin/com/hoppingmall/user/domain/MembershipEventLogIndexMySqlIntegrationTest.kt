package com.hoppingmall.user.domain

import com.hoppingmall.user.config.JpaConfig
import com.hoppingmall.user.support.MySqlJpaTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.Import

@DisplayName("멤버십 이벤트 기록 조회 인덱스 MySQL 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@Import(JpaConfig::class)
class MembershipEventLogIndexMySqlIntegrationTest : MySqlJpaTestSupport() {

    @Test
    fun 결제_ID로_구매_기록을_찾는_조회는_인덱스를_탄다() {
        val plan = jdbcTemplate.queryForMap(
            """
            EXPLAIN SELECT COUNT(id) > 0 FROM membership_event_logs
            WHERE payment_id = 1 AND (type IS NULL OR type = 'PURCHASE')
            """.trimIndent()
        )

        assertThat(plan["key"]).isEqualTo("idx_membership_event_logs_payment_id")
        assertThat(plan["type"]).isNotEqualTo("ALL")
    }
}
