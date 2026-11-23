package com.hoppingmall.user.auth.domain

import com.hoppingmall.user.config.JpaConfig
import com.hoppingmall.user.support.MySqlJpaTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.Import
import org.springframework.dao.DuplicateKeyException

@DisplayName("세션 표 MySQL 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@Import(JpaConfig::class)
class UserSessionSchemaMySqlIntegrationTest : MySqlJpaTestSupport() {

    @Test
    fun 사용자의_활성_세션_조회는_사용자별_인덱스를_탄다() {
        val plan = jdbcTemplate.queryForMap(
            """
            EXPLAIN SELECT id FROM user_sessions
            WHERE user_id = 1 AND revoked_at IS NULL AND token_expires_at > NOW()
            ORDER BY token_issued_at, id
            """.trimIndent()
        )

        assertThat(plan["key"]).isEqualTo("idx_user_sessions_user_id_token_issued_at")
        assertThat(plan["type"]).isNotEqualTo("ALL")
    }

    @Test
    fun 같은_세션_id는_두_번_저장되지_않는다() {
        insertSession("22222222-2222-2222-2222-222222222222", 1L)

        assertThatThrownBy { insertSession("22222222-2222-2222-2222-222222222222", 2L) }
            .isInstanceOf(DuplicateKeyException::class.java)
    }

    @Test
    fun 토큰은_64자_해시_열에_시각은_마이크로초_열에_둔다() {
        val columns = jdbcTemplate.queryForList(
            """
            SELECT COLUMN_NAME, COLUMN_TYPE FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_sessions'
            """.trimIndent()
        ).associate { it["COLUMN_NAME"] to it["COLUMN_TYPE"] }

        assertThat(columns["token_hash"]).isEqualTo("varchar(64)")
        assertThat(columns["previous_token_hash"]).isEqualTo("varchar(64)")
        assertThat(columns["token_issued_at"]).isEqualTo("datetime(6)")
        assertThat(columns["token_expires_at"]).isEqualTo("datetime(6)")
    }

    private fun insertSession(sessionId: String, userId: Long) {
        jdbcTemplate.update(
            """
            INSERT INTO user_sessions (session_id, user_id, token_hash, generation, token_issued_at, token_expires_at, created_at)
            VALUES (?, ?, ?, 1, NOW(), NOW() + INTERVAL 1 DAY, NOW())
            """.trimIndent(),
            sessionId, userId, "a".repeat(64)
        )
    }
}
