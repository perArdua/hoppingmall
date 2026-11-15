package com.hoppingmall.gateway

import com.hoppingmall.gateway.config.ConsistencyMonitorRouteConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.mock.env.MockEnvironment

@DisplayName("프로필별 consistency-monitor 주소")
@DisplayNameGeneration(ReplaceUnderscores::class)
class ConsistencyMonitorRouteUriTest {

    @ParameterizedTest
    @CsvSource(
        "'', http://localhost:8087",
        "docker, http://consistency-monitor:8087",
        "k8s, http://consistency-monitor.hoppingmall.svc.cluster.local:8087"
    )
    fun 주소를_따로_주지_않으면_프로필에_맞는_기본_주소를_쓴다(profile: String, expected: String) {
        val environment = MockEnvironment()
        if (profile.isNotBlank()) environment.setActiveProfiles(profile)

        assertThat(ConsistencyMonitorRouteConfig.monitorUri(environment)).isEqualTo(expected)
    }

    @Test
    fun ROUTES_CONSISTENCY_MONITOR를_주면_프로필보다_먼저_쓴다() {
        val environment = MockEnvironment().withProperty("ROUTES_CONSISTENCY_MONITOR", "http://monitor.internal:9000")
        environment.setActiveProfiles("docker")

        assertThat(ConsistencyMonitorRouteConfig.monitorUri(environment)).isEqualTo("http://monitor.internal:9000")
    }
}
