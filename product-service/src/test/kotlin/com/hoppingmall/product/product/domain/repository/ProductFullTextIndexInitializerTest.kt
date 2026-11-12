package com.hoppingmall.product.product.domain.repository

import org.assertj.core.api.Assertions.assertThatCode
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
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import java.sql.Connection
import java.sql.DatabaseMetaData
import javax.sql.DataSource

@DisplayName("ProductFullTextIndexInitializer")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class ProductFullTextIndexInitializerTest {

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

    @BeforeEach
    fun setUp() {
        whenever(dataSource.connection).thenReturn(connection)
        whenever(connection.metaData).thenReturn(metaData)
    }

    @Test
    fun MySQL이_아니면_인덱스를_확인하거나_만들지_않는다() {
        whenever(metaData.databaseProductName).thenReturn("H2")

        initializer.ensureIndex()

        verify(jdbcTemplate, never()).queryForList(any<String>(), eq(String::class.java))
        verify(jdbcTemplate, never()).execute(any<String>())
    }

    @Test
    fun 이름과_설명의_FULLTEXT_인덱스가_있으면_만들지_않는다() {
        whenever(metaData.databaseProductName).thenReturn("MySQL")
        whenever(jdbcTemplate.queryForList(any<String>(), eq(String::class.java))).thenReturn(listOf("name", "description,name"))

        initializer.ensureIndex()

        verify(jdbcTemplate, never()).execute(any<String>())
    }

    @Test
    fun 인덱스가_없으면_만든다() {
        whenever(metaData.databaseProductName).thenReturn("MySQL")
        whenever(jdbcTemplate.queryForList(any<String>(), eq(String::class.java))).thenReturn(listOf("name"))

        initializer.afterPropertiesSet()

        verify(jdbcTemplate).execute(
            "ALTER TABLE products ADD FULLTEXT INDEX ft_product_search (name, description) WITH PARSER ngram"
        )
    }

    @Test
    fun 만들다_실패해도_그사이_다른_인스턴스가_만들었으면_그대로_기동한다() {
        whenever(metaData.databaseProductName).thenReturn("MySQL")
        whenever(jdbcTemplate.queryForList(any<String>(), eq(String::class.java)))
            .thenReturn(emptyList())
            .thenReturn(listOf("description,name"))
        doThrow(DataIntegrityViolationException("Duplicate key name")).whenever(jdbcTemplate).execute(any<String>())

        assertThatCode { initializer.ensureIndex() }.doesNotThrowAnyException()

        verify(jdbcTemplate, times(2)).queryForList(any<String>(), eq(String::class.java))
    }

    @Test
    fun 만들기에_실패하고_인덱스도_없으면_기동은_막지_않는다() {
        whenever(metaData.databaseProductName).thenReturn("MySQL")
        whenever(jdbcTemplate.queryForList(any<String>(), eq(String::class.java))).thenReturn(emptyList())
        doThrow(DataIntegrityViolationException("denied")).whenever(jdbcTemplate).execute(any<String>())

        assertThatCode { initializer.ensureIndex() }.doesNotThrowAnyException()

        verify(jdbcTemplate, times(2)).queryForList(any<String>(), eq(String::class.java))
    }
}
