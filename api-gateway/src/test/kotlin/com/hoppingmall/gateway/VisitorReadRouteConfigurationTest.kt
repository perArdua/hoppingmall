package com.hoppingmall.gateway

import com.hoppingmall.gateway.config.VisitorReadRouteConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.core.env.PropertySource
import org.springframework.core.io.ClassPathResource

@DisplayName("프로필별 방문자 조회 라우트가 기대는 상품 라우트")
@DisplayNameGeneration(ReplaceUnderscores::class)
class VisitorReadRouteConfigurationTest {

    private fun load(file: String): PropertySource<*> =
        YamlPropertySourceLoader().load(file, ClassPathResource(file)).single()

    private fun PropertySource<*>.routeIds(): List<String> =
        generateSequence(0) { it + 1 }
            .map { getProperty("spring.cloud.gateway.routes[$it].id")?.toString() }
            .takeWhile { it != null }
            .filterNotNull()
            .toList()

    @ParameterizedTest
    @ValueSource(strings = ["application.yml", "application-docker.yml", "application-k8s.yml"])
    fun 방문자_조회_라우트가_주소를_가져오는_상품_라우트가_있다(file: String) {
        assertThat(load(file).routeIds()).contains(VisitorReadRouteConfig.PRODUCT_SERVICE_ROUTE_ID)
    }
}
