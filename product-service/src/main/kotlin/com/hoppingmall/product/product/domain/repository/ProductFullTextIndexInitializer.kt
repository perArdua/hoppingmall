package com.hoppingmall.product.product.domain.repository

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.InitializingBean
import org.springframework.context.annotation.DependsOn
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import javax.sql.DataSource

@Component
@DependsOn("entityManagerFactory")
class ProductFullTextIndexInitializer(
    private val dataSource: DataSource,
    private val jdbcTemplate: JdbcTemplate
) : InitializingBean {

    private val logger = LoggerFactory.getLogger(ProductFullTextIndexInitializer::class.java)

    override fun afterPropertiesSet() {
        ensureIndex()
    }

    fun ensureIndex() {
        if (!isMySql() || hasFullTextIndex()) return
        try {
            jdbcTemplate.execute(CREATE_INDEX_SQL)
            logger.info("상품 검색 FULLTEXT 인덱스 생성: {}", INDEX_NAME)
        } catch (e: DataAccessException) {
            if (hasFullTextIndex()) return
            logger.error("상품 검색 FULLTEXT 인덱스 생성 실패, 키워드 검색이 동작하지 않습니다", e)
        }
    }

    fun hasFullTextIndex(): Boolean =
        jdbcTemplate.queryForList(FULLTEXT_COLUMNS_SQL, String::class.java).any { it == INDEXED_COLUMNS }

    private fun isMySql(): Boolean =
        dataSource.connection.use { it.metaData.databaseProductName.equals("MySQL", ignoreCase = true) }

    companion object {
        const val INDEX_NAME = "ft_product_search"
        private const val INDEXED_COLUMNS = "description,name"
        private const val CREATE_INDEX_SQL =
            "ALTER TABLE products ADD FULLTEXT INDEX $INDEX_NAME (name, description) WITH PARSER ngram"
        private const val FULLTEXT_COLUMNS_SQL =
            "SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY COLUMN_NAME) FROM information_schema.STATISTICS " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'products' AND INDEX_TYPE = 'FULLTEXT' GROUP BY INDEX_NAME"
    }
}
