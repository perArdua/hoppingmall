package com.hoppingmall.gateway

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.core.env.PropertySource
import org.springframework.core.io.ClassPathResource

@DisplayName("프로필별 게이트웨이 타임아웃 설정")
@DisplayNameGeneration(ReplaceUnderscores::class)
class GatewayTimeoutConfigurationTest {

    private fun load(file: String): PropertySource<*> =
        YamlPropertySourceLoader().load(file, ClassPathResource(file)).single()

    private fun PropertySource<*>.value(key: String): String? = getProperty(key)?.toString()

    private fun PropertySource<*>.routeIndex(id: String): Int =
        generateSequence(0) { it + 1 }
            .takeWhile { value("spring.cloud.gateway.routes[$it].id") != null }
            .firstOrNull { value("spring.cloud.gateway.routes[$it].id") == id } ?: -1

    private fun PropertySource<*>.routeIndexByPath(path: String): Int =
        generateSequence(0) { it + 1 }
            .takeWhile { value("spring.cloud.gateway.routes[$it].id") != null }
            .firstOrNull { value("spring.cloud.gateway.routes[$it].predicates[0]") == "Path=$path" } ?: -1

    private fun PropertySource<*>.filterNames(index: Int): List<String> =
        generateSequence(0) { it + 1 }
            .map { value("spring.cloud.gateway.routes[$index].filters[$it]") ?: value("spring.cloud.gateway.routes[$index].filters[$it].name") }
            .takeWhile { it != null }
            .filterNotNull()
            .toList()

    @ParameterizedTest
    @ValueSource(strings = ["application.yml", "application-docker.yml", "application-k8s.yml"])
    fun 뒷단_연결은_2초_응답은_10초로_끊는다(file: String) {
        val source = load(file)

        assertThat(source.value("spring.cloud.gateway.httpclient.connect-timeout")).isEqualTo("2000")
        assertThat(source.value("spring.cloud.gateway.httpclient.response-timeout")).isEqualTo("10s")
    }

    @ParameterizedTest
    @ValueSource(strings = ["application.yml", "application-docker.yml", "application-k8s.yml"])
    fun 알림_구독_라우트는_응답_타임아웃을_끄고_JWT를_검증하며_알림_라우트보다_먼저_맞춰진다(file: String) {
        val source = load(file)
        val subscribeIndex = source.routeIndexByPath("/api/v1/notifications/subscribe")
        val notificationIndex = source.routeIndex("notification-service")

        assertThat(subscribeIndex).isGreaterThanOrEqualTo(0)
        assertThat(source.value("spring.cloud.gateway.routes[$subscribeIndex].metadata.response-timeout")).isEqualTo("-1")
        assertThat(source.filterNames(subscribeIndex)).containsExactly("JwtValidation")
        assertThat(source.value("spring.cloud.gateway.routes[$subscribeIndex].uri"))
            .isEqualTo(source.value("spring.cloud.gateway.routes[$notificationIndex].uri"))
        assertThat(subscribeIndex).isLessThan(notificationIndex)
    }

    @ParameterizedTest
    @ValueSource(strings = ["application.yml", "application-docker.yml", "application-k8s.yml"])
    fun 알림_구독_말고는_응답_타임아웃을_라우트별로_바꾼_곳이_없다(file: String) {
        val source = load(file)
        val subscribeIndex = source.routeIndexByPath("/api/v1/notifications/subscribe")
        val overridden = generateSequence(0) { it + 1 }
            .takeWhile { source.value("spring.cloud.gateway.routes[$it].id") != null }
            .filter { source.value("spring.cloud.gateway.routes[$it].metadata.response-timeout") != null }
            .toList()

        assertThat(overridden).containsExactly(subscribeIndex)
    }
}
