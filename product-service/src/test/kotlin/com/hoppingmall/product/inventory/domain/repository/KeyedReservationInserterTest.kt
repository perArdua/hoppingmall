package com.hoppingmall.product.inventory.domain.repository

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.hoppingmall.product.inventory.domain.InventoryReservation
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.dao.CannotAcquireLockException
import org.springframework.dao.DataAccessException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.DuplicateKeyException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.PreparedStatementSetter
import java.sql.SQLException

@DisplayName("KeyedReservationInserter")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class KeyedReservationInserterTest {

    @Mock
    private lateinit var jdbcTemplate: JdbcTemplate

    @InjectMocks
    private lateinit var inserter: KeyedReservationInserter

    private val logs = ListAppender<ILoggingEvent>()
    private val inserterLogger = LoggerFactory.getLogger(KeyedReservationInserter::class.java) as Logger

    @BeforeEach
    fun setUp() {
        logs.start()
        inserterLogger.addAppender(logs)
    }

    @AfterEach
    fun tearDown() {
        inserterLogger.detachAppender(logs)
    }

    @Test
    fun 요청_키_충돌은_로그_없이_그대로_던진다() {
        listOf(
            "Duplicate entry 'k-1' for key 'inventory_reservations.uk_reservation_request_product'",
            "Unique index or primary key violation: PUBLIC.UK_RESERVATION_REQUEST_PRODUCT_INDEX_8"
        ).forEach { message ->
            assertRethrown(DuplicateKeyException("insert", SQLException(message)))
        }

        assertThat(errorMessages()).isEmpty()
    }

    @Test
    fun 다른_유니크_위반은_ERROR를_남기고_던진다() {
        assertRethrown(DuplicateKeyException("insert", SQLException("Duplicate entry for key 'idx_reservation_id'")))

        assertThat(errorMessages()).singleElement().asString().contains("idx_reservation_id")
    }

    @Test
    fun 유니크가_아닌_제약_위반은_ERROR를_남기고_던진다() {
        assertRethrown(DataIntegrityViolationException("insert", SQLException("Data too long for column 'request_key'")))

        assertThat(errorMessages()).singleElement().asString().contains("Data too long")
    }

    @Test
    fun 잠금_실패는_ERROR를_남기고_던진다() {
        assertRethrown(CannotAcquireLockException("insert", SQLException("Deadlock found when trying to get lock")))

        assertThat(errorMessages()).singleElement().asString().contains("Deadlock found")
    }

    @Test
    fun MySQL_교착은_다시_할_수_있어_ERROR_없이_던진다() {
        assertRethrown(CannotAcquireLockException("insert", SQLException("Deadlock found when trying to get lock", "40001", 1213)))

        assertThat(errorMessages()).isEmpty()
    }

    @Test
    fun 잠금_대기_시간_초과는_교착이_아니라_ERROR를_남긴다() {
        assertRethrown(CannotAcquireLockException("insert", SQLException("Lock wait timeout exceeded", "40001", 1205)))

        assertThat(errorMessages()).singleElement().asString().contains("Lock wait timeout")
    }

    @Test
    fun 다른_SQL처럼_추적_주석을_앞에_붙인다() {
        MDC.putCloseable("traceId", "trace-1").use {
            inserter.insert(InventoryReservation.create(1L, 1, 600L, "k"))
        }

        verify(jdbcTemplate).update(argThat<String> { startsWith("/* traceId=trace-1 */") }, any<PreparedStatementSetter>())
    }

    private fun assertRethrown(failure: DataAccessException) {
        whenever(jdbcTemplate.update(any<String>(), any<PreparedStatementSetter>())).thenThrow(failure)

        assertThatThrownBy { inserter.insert(InventoryReservation.create(1L, 1, 600L, "k")) }.isSameAs(failure)
    }

    private fun errorMessages() = logs.list.filter { it.level == Level.ERROR }.map { it.formattedMessage }
}
