package com.hoppingmall.gateway

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.core.env.PropertySource
import org.springframework.core.io.ClassPathResource
import java.util.stream.Stream

@DisplayName("프로필별 운영 API 라우트 설정")
@DisplayNameGeneration(ReplaceUnderscores::class)
class OperationalRouteConfigurationTest {

    companion object {
        private val FILES = listOf("application.yml", "application-docker.yml", "application-k8s.yml")

        private val DLQ_SERVICES = mapOf(
            "order" to "order-service-orders",
            "product" to "product-service-products",
            "user" to "user-service",
            "notification" to "notification-service",
            "payment" to "payment-service-payments"
        )

        @JvmStatic
        fun dlqRoutes(): Stream<Arguments> =
            FILES.flatMap { file -> DLQ_SERVICES.map { (service, serviceRoute) -> Arguments.of(file, service, serviceRoute) } }
                .stream()
    }

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

    private fun PropertySource<*>.filters(index: Int): List<String> =
        generateSequence(0) { it + 1 }
            .map { value("spring.cloud.gateway.routes[$index].filters[$it]") ?: value("spring.cloud.gateway.routes[$index].filters[$it].name") }
            .takeWhile { it != null }
            .filterNotNull()
            .toList()

    @ParameterizedTest
    @MethodSource("dlqRoutes")
    fun 서비스별_DLQ_관리_라우트는_그_서비스로_가고_ADMIN만_통과시키며_기존_DLQ_라우트보다_먼저_맞춰진다(
        file: String,
        service: String,
        serviceRoute: String
    ) {
        val source = load(file)
        val index = source.routeIndexByPath("/api/v1/admin/dlq/$service/**")

        assertThat(index).isGreaterThanOrEqualTo(0)
        assertThat(source.value("spring.cloud.gateway.routes[$index].uri"))
            .isEqualTo(source.value("spring.cloud.gateway.routes[${source.routeIndex(serviceRoute)}].uri"))
        assertThat(source.filters(index)).containsExactly(
            "JwtValidation=ADMIN",
            "RewritePath=/api/v1/admin/dlq/$service/(?<segment>.*), /api/v1/admin/dlq/\$\\{segment}"
        )
        assertThat(index).isLessThan(source.routeIndex("payment-service-admin-dlq"))
    }

    @ParameterizedTest
    @ValueSource(strings = ["application.yml", "application-docker.yml", "application-k8s.yml"])
    fun 파일_업로드_라우트는_product로_가고_JWT를_검증한다(file: String) {
        val source = load(file)
        val index = source.routeIndexByPath("/api/v1/files/**")

        assertThat(index).isGreaterThanOrEqualTo(0)
        assertThat(source.value("spring.cloud.gateway.routes[$index].uri"))
            .isEqualTo(source.value("spring.cloud.gateway.routes[${source.routeIndex("product-service-products")}].uri"))
        assertThat(source.filters(index)).containsExactly("JwtValidation")
    }
}
