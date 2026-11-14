package com.hoppingmall.consistency.monitor

import com.hoppingmall.consistency.support.MySqlContainerSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource
import org.springframework.core.io.support.PropertiesLoaderUtils
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator
import org.springframework.scheduling.annotation.Scheduled

@DisplayName("MonitorLease 임대 갱신")
@DisplayNameGeneration(ReplaceUnderscores::class)
class MonitorLeaseRenewalMySqlTest {

    private val dataSource = DriverManagerDataSource(
        MySqlContainerSupport.jdbcUrl("consistency_lease_renewal"),
        MySqlContainerSupport.username,
        MySqlContainerSupport.password
    )
    private val jdbcTemplate = JdbcTemplate(dataSource)

    @BeforeEach
    fun setUp() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS invariant_violations")
        jdbcTemplate.execute("DROP TABLE IF EXISTS monitor_leases")
        ResourceDatabasePopulator(ClassPathResource("schema.sql")).execute(dataSource)
    }

    private fun lease(owner: String) = MonitorLease(jdbcTemplate, LEASE_SECONDS, owner)

    private fun remainingSeconds(): Long =
        jdbcTemplate.queryForObject(
            "SELECT TIMESTAMPDIFF(SECOND, NOW(6), lease_until) FROM monitor_leases",
            Long::class.java
        )!!

    private fun holder(): String =
        jdbcTemplate.queryForObject("SELECT owner FROM monitor_leases", String::class.java)!!

    private fun expireSoon() {
        jdbcTemplate.update("UPDATE monitor_leases SET lease_until = NOW(6) + INTERVAL 5 SECOND")
    }

    @Test
    fun 임대를_쥔_인스턴스가_갱신하면_임대가_처음_길이로_다시_늘어난다() {
        val first = lease("monitor-a")
        val second = lease("monitor-b")
        assertThat(first.tryAcquire()).isTrue()
        expireSoon()

        first.renew()

        assertThat(remainingSeconds()).isBetween(LEASE_SECONDS - 5, LEASE_SECONDS)
        assertThat(second.tryAcquire()).isFalse()
    }

    @Test
    fun 임대를_쥐지_않은_인스턴스의_갱신은_임대를_바꾸지_않는다() {
        val first = lease("monitor-a")
        val second = lease("monitor-b")
        assertThat(first.tryAcquire()).isTrue()
        expireSoon()

        second.renew()

        assertThat(holder()).isEqualTo("monitor-a")
        assertThat(remainingSeconds()).isLessThanOrEqualTo(5)
    }

    @Test
    fun 반납한_뒤의_갱신은_임대를_되살리지_않는다() {
        val first = lease("monitor-a")
        val second = lease("monitor-b")
        assertThat(first.tryAcquire()).isTrue()
        first.release()

        first.renew()

        assertThat(second.tryAcquire()).isTrue()
    }

    @Test
    fun 기본_임대는_60초이고_갱신은_20초마다_점검과_다른_스레드에서_돈다() {
        val properties = PropertiesLoaderUtils.loadProperties(ClassPathResource("application.properties"))
        val renew = MonitorLease::class.java.getMethod("renew").getAnnotation(Scheduled::class.java)

        assertThat(properties.getProperty("consistency.lease.seconds")).isEqualTo("60")
        assertThat(properties.getProperty("consistency.lease.renew-ms")).isEqualTo("20000")
        assertThat(properties.getProperty("spring.task.scheduling.pool.size")).isEqualTo("2")
        assertThat(renew).isNotNull
        assertThat(renew.fixedDelayString).isEqualTo("\${consistency.lease.renew-ms:20000}")
    }

    companion object {
        private const val LEASE_SECONDS = 60L
    }
}
