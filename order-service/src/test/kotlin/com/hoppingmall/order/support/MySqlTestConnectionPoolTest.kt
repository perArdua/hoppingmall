package com.hoppingmall.order.support

import com.zaxxer.hikari.HikariDataSource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.SpringProperties
import org.springframework.test.context.cache.ContextCache
import javax.sql.DataSource

@DisplayName("MySQL 통합 테스트 컨텍스트의 연결 풀 크기")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class MySqlTestConnectionPoolTest : MySqlJpaTestSupport() {

    @Autowired
    private lateinit var dataSource: DataSource

    @Test
    fun 테스트_컨텍스트의_풀은_운영_설정보다_작고_쉬는_연결을_쥐고_있지_않는다() {
        val pool = dataSource.unwrap(HikariDataSource::class.java)

        assertThat(pool.maximumPoolSize).isEqualTo(5)
        assertThat(pool.minimumIdle).isZero()
        assertThat(pool.idleTimeout).isEqualTo(10_000L)
    }

    @Test
    fun 캐시에_남을_수_있는_컨텍스트가_모두_풀을_가득_채워도_테스트_컨테이너의_연결_한도_안에_든다() {
        val maxConnections = jdbcTemplate.queryForObject("SELECT @@max_connections", Long::class.java)!!
        val pool = dataSource.unwrap(HikariDataSource::class.java)
        val cacheMaxSize = SpringProperties.getProperty(ContextCache.MAX_CONTEXT_CACHE_SIZE_PROPERTY_NAME)!!.toLong()

        assertThat(cacheMaxSize).isEqualTo(25L)
        assertThat(pool.maximumPoolSize * cacheMaxSize).isLessThan(maxConnections)
    }
}
