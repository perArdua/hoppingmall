package com.hoppingmall.product.product.domain.repository

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
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
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.slf4j.LoggerFactory
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.jdbc.BadSqlGrammarException
import org.springframework.jdbc.core.JdbcTemplate
import java.sql.Connection
import java.sql.DatabaseMetaData
import java.sql.SQLException
import javax.sql.DataSource

@DisplayName("FULLTEXT 인덱스를 만들지 못했을 때의 기동")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class ProductFullTextIndexInitializerStartupTest {

    @Mock
    private lateinit var dataSource: DataSource

    @Mock
    private lateinit var jdbcTemplate: JdbcTemplate

    @Mock
    private lateinit var connection: Connection

    @Mock
    private lateinit var metaData: DatabaseMetaData

    @InjectMocks
    private lateinit var initializer: ProductFullTextIndexInitializer

    private val appender = ListAppender<ILoggingEvent>()
    private val logger = LoggerFactory.getLogger(ProductFullTextIndexInitializer::class.java) as Logger

    @BeforeEach
    fun setUp() {
        whenever(dataSource.connection).thenReturn(connection)
        whenever(connection.metaData).thenReturn(metaData)
        whenever(metaData.databaseProductName).thenReturn("MySQL")
        appender.start()
        logger.addAppender(appender)
    }

    @AfterEach
    fun tearDown() {
        logger.detachAppender(appender)
        appender.stop()
    }

    @Test
    fun 인덱스_생성_문장이_실패하면_기동은_계속되고_ERROR_로그를_남긴다() {
        whenever(jdbcTemplate.queryForList(any<String>(), eq(String::class.java))).thenReturn(emptyList())
        doThrow(BadSqlGrammarException("create", "ALTER TABLE products", SQLException("denied")))
            .whenever(jdbcTemplate).execute(any<String>())

        assertThatCode { initializer.afterPropertiesSet() }.doesNotThrowAnyException()

        assertThat(errorLogs()).hasSize(1)
    }

    @Test
    fun 인덱스가_있는지_확인하는_조회가_실패해도_기동은_계속되고_ERROR_로그를_남긴다() {
        whenever(jdbcTemplate.queryForList(any<String>(), eq(String::class.java)))
            .thenThrow(DataAccessResourceFailureException("information_schema"))

        assertThatCode { initializer.afterPropertiesSet() }.doesNotThrowAnyException()

        assertThat(errorLogs()).hasSize(1)
    }

    private fun errorLogs(): List<ILoggingEvent> =
        appender.list.filter { it.level == Level.ERROR }
}
