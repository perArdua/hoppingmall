package com.hoppingmall.order.config

import com.hoppingmall.order.support.MySqlContainerSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.boot.DefaultBootstrapContext
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor
import org.springframework.core.env.StandardEnvironment
import org.springframework.core.io.DefaultResourceLoader
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import java.sql.Timestamp
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.TimeZone

@DisplayName("docker·k8s 프로필 JDBC 연결의 시간대")
@DisplayNameGeneration(ReplaceUnderscores::class)
class ProfileJdbcTimeZoneMySqlTest {

    private val originalTimeZone: TimeZone = TimeZone.getDefault()

    @AfterEach
    fun restoreTimeZone() {
        TimeZone.setDefault(originalTimeZone)
    }

    private fun profileQuery(profile: String): String {
        val environment = StandardEnvironment()
        environment.propertySources.remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME)
        ConfigDataEnvironmentPostProcessor.applyTo(
            environment,
            DefaultResourceLoader(),
            DefaultBootstrapContext(),
            listOf(profile)
        )
        return environment.getProperty("spring.datasource.url")!!.substringAfter("?")
    }

    private fun jdbcTemplate(query: String): JdbcTemplate =
        JdbcTemplate(
            DriverManagerDataSource(
                MySqlContainerSupport.jdbcUrl(DATABASE) + "&" + query,
                MySqlContainerSupport.username,
                MySqlContainerSupport.password
            )
        )

    private fun recreateProbeTable() {
        val jdbc = jdbcTemplate(BEFORE_QUERY)
        jdbc.execute("DROP TABLE IF EXISTS time_zone_probe")
        jdbc.execute(
            "CREATE TABLE time_zone_probe (id BIGINT PRIMARY KEY, via_timestamp DATETIME(6) NOT NULL, via_object DATETIME(6) NOT NULL)"
        )
    }

    private fun write(jdbc: JdbcTemplate, id: Long, value: LocalDateTime) {
        jdbc.update("INSERT INTO time_zone_probe (id, via_timestamp, via_object) VALUES (?, ?, ?)") { ps ->
            ps.setLong(1, id)
            ps.setTimestamp(2, Timestamp.valueOf(value))
            ps.setObject(3, value)
        }
    }

    private fun read(jdbc: JdbcTemplate, id: Long): Pair<LocalDateTime, LocalDateTime> =
        jdbc.queryForObject("SELECT via_timestamp, via_object FROM time_zone_probe WHERE id = ?", { rs, _ ->
            rs.getTimestamp("via_timestamp").toLocalDateTime() to rs.getObject("via_object", LocalDateTime::class.java)
        }, id)!!

    private fun stored(jdbc: JdbcTemplate, id: Long): Pair<String, String> =
        jdbc.queryForObject(
            "SELECT CAST(via_timestamp AS CHAR), CAST(via_object AS CHAR) FROM time_zone_probe WHERE id = ?",
            { rs, _ -> rs.getString(1) to rs.getString(2) },
            id
        )!!

    @ParameterizedTest
    @ValueSource(strings = ["docker", "k8s"])
    fun 프로필_연결의_NOW는_한국_시각_벽시계다(profile: String) {
        val dbNow = jdbcTemplate(profileQuery(profile))
            .queryForObject("SELECT CAST(NOW(6) AS CHAR)", String::class.java)!!

        val difference = Duration.between(LocalDateTime.parse(dbNow.replace(' ', 'T')), LocalDateTime.now(KST)).abs()

        assertThat(difference).isLessThan(Duration.ofMinutes(5))
    }

    @ParameterizedTest
    @ValueSource(strings = ["docker", "k8s"])
    fun 세션_시간대를_맞추기_전에_쓴_시각을_프로필_연결로_읽어도_같은_값이다(profile: String) {
        recreateProbeTable()
        JVM_ZONES.forEachIndexed { index, zone ->
            TimeZone.setDefault(TimeZone.getTimeZone(zone))
            write(jdbcTemplate(BEFORE_QUERY), index.toLong(), VALUE)

            assertThat(read(jdbcTemplate(profileQuery(profile)), index.toLong())).isEqualTo(VALUE to VALUE)
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["docker", "k8s"])
    fun 프로필_연결로_쓴_시각은_맞추기_전과_같은_벽시계로_저장된다(profile: String) {
        recreateProbeTable()
        JVM_ZONES.forEachIndexed { index, zone ->
            TimeZone.setDefault(TimeZone.getTimeZone(zone))
            val beforeId = index * 2L
            val afterId = beforeId + 1
            write(jdbcTemplate(BEFORE_QUERY), beforeId, VALUE)
            write(jdbcTemplate(profileQuery(profile)), afterId, VALUE)

            assertThat(stored(jdbcTemplate(BEFORE_QUERY), afterId)).isEqualTo(stored(jdbcTemplate(BEFORE_QUERY), beforeId))
            assertThat(read(jdbcTemplate(BEFORE_QUERY), afterId)).isEqualTo(VALUE to VALUE)
        }
    }

    companion object {
        private const val DATABASE = "time_zone_probe_db"
        private const val BEFORE_QUERY = "useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Seoul"
        private val KST: ZoneId = ZoneId.of("Asia/Seoul")
        private val JVM_ZONES = listOf("UTC", "Asia/Seoul")
        private val VALUE: LocalDateTime = LocalDateTime.of(2026, 11, 15, 1, 30, 15, 123_456_000)
    }
}
