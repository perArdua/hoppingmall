package com.hoppingmall.common.consumer

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.transaction.support.TransactionTemplate

@DisplayName("IdempotentConsumeTemplate 무결성 위반 구분")
@DisplayNameGeneration(ReplaceUnderscores::class)
class IdempotentConsumeUniqueViolationTest {

    private val logger = LoggerFactory.getLogger(javaClass)
    private val dataSource = DriverManagerDataSource("jdbc:h2:mem:idempotent-consume;DB_CLOSE_DELAY=-1", "sa", "")
    private val jdbcTemplate = JdbcTemplate(dataSource)
    private val transactionTemplate = TransactionTemplate(DataSourceTransactionManager(dataSource))

    @BeforeEach
    fun setUp() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS event_logs")
        jdbcTemplate.execute("DROP TABLE IF EXISTS product_stats")
        jdbcTemplate.execute("CREATE TABLE event_logs (id BIGINT AUTO_INCREMENT PRIMARY KEY, event_id VARCHAR(64) NOT NULL UNIQUE)")
        jdbcTemplate.execute("CREATE TABLE product_stats (id BIGINT AUTO_INCREMENT PRIMARY KEY, product_id BIGINT NOT NULL UNIQUE, sales BIGINT NOT NULL)")
    }

    private fun processed(eventId: String): Boolean =
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM event_logs WHERE event_id = ?", Long::class.java, eventId)!! > 0

    private fun salesOf(productId: Long): Long =
        jdbcTemplate.queryForObject("SELECT COALESCE(SUM(sales), 0) FROM product_stats WHERE product_id = ?", Long::class.java, productId)!!

    private fun consume(eventId: String, productId: Long, existsCheck: () -> Boolean = { processed(eventId) }) {
        executeIdempotently(
            eventId = eventId,
            eventDescription = "통계",
            logger = logger,
            existsCheck = existsCheck
        ) {
            transactionTemplate.executeWithoutResult {
                jdbcTemplate.update("INSERT INTO product_stats (product_id, sales) VALUES (?, 1)", productId)
                jdbcTemplate.update("INSERT INTO event_logs (event_id) VALUES (?)", eventId)
            }
        }
    }

    @Test
    fun 처리_기록이_아닌_유니크_제약_위반은_이미_처리됨으로_넘기지_않고_다시_던진다() {
        jdbcTemplate.update("INSERT INTO product_stats (product_id, sales) VALUES (1, 1)")

        assertThatThrownBy { consume("tx-1", productId = 1L) }
            .isInstanceOf(DataIntegrityViolationException::class.java)

        assertThat(processed("tx-1")).isFalse()
        assertThat(salesOf(1L)).isEqualTo(1L)
    }

    @Test
    fun 처리_기록의_유니크_제약_위반은_이미_처리된_것으로_보고_넘긴다() {
        var checks = 0
        jdbcTemplate.update("INSERT INTO event_logs (event_id) VALUES ('tx-2')")

        assertThatCode {
            consume("tx-2", productId = 2L, existsCheck = { checks++ > 0 && processed("tx-2") })
        }.doesNotThrowAnyException()

        assertThat(salesOf(2L)).isZero()
    }

    @Test
    fun 위반_뒤_처리_기록_확인이_실패하면_원래_무결성_위반을_던진다() {
        var checks = 0
        val violation = DataIntegrityViolationException("Duplicate entry")

        assertThatThrownBy {
            executeIdempotently(
                eventId = "tx-3",
                eventDescription = "통계",
                logger = logger,
                existsCheck = { if (checks++ == 0) false else throw IllegalStateException("DB 연결 실패") }
            ) { throw violation }
        }.isSameAs(violation)
    }
}
