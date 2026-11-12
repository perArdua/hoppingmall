package com.hoppingmall.gateway

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.core.env.PropertySource
import org.springframework.core.io.ClassPathResource

@DisplayName("프로필별 서비스 API 문서 라우트 설정")
@DisplayNameGeneration(ReplaceUnderscores::class)
class ApiDocsRouteConfigurationTest {

    private fun load(file: String): PropertySource<*> =
        YamlPropertySourceLoader().load(file, ClassPathResource(file)).single()

    private fun PropertySource<*>.value(key: String): String? = getProperty(key)?.toString()

    private fun PropertySource<*>.routeIndices(): List<Int> =
        generateSequence(0) { it + 1 }
            .takeWhile { value("spring.cloud.gateway.routes[$it].id") != null }
            .toList()

    private fun PropertySource<*>.paths(index: Int): List<String> =
        generateSequence(0) { it + 1 }
            .map { value("spring.cloud.gateway.routes[$index].predicates[$it]") }
            .takeWhile { it != null }
            .filterNotNull()
            .filter { it.startsWith("Path=") }
            .flatMap { it.removePrefix("Path=").split(",") }
            .toList()

    private fun PropertySource<*>.filters(index: Int): List<String> =
        generateSequence(0) { it + 1 }
            .map { value("spring.cloud.gateway.routes[$index].filters[$it]") ?: value("spring.cloud.gateway.routes[$index].filters[$it].name") }
            .takeWhile { it != null }
            .filterNotNull()
            .toList()

    private fun PropertySource<*>.apiDocsRoutes(): List<Int> =
        routeIndices().filter { index -> paths(index).any { it.startsWith("/v3/api-docs") } }

    @ParameterizedTest
    @ValueSource(strings = ["application.yml", "application-docker.yml"])
    fun 서비스_API_문서_라우트는_6개_모두_ADMIN만_통과시키고_내부_API_경로를_뺀다(file: String) {
        val source = load(file)
        val docsRoutes = source.apiDocsRoutes()

        assertThat(docsRoutes).hasSize(6)
        assertThat(docsRoutes).allSatisfy { index ->
            val filters = source.filters(index)
            assertThat(filters.first()).isEqualTo("JwtValidation=ADMIN")
            assertThat(filters).contains("RemoveInternalApiDocs")
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["application.yml", "application-docker.yml", "application-k8s.yml"])
    fun 어느_라우트도_내부_API_경로를_게이트웨이로_내보내지_않는다(file: String) {
        val source = load(file)

        assertThat(source.routeIndices().flatMap { source.paths(it) })
            .isNotEmpty()
            .noneMatch { it.startsWith("/internal") }
    }

    @Test
    fun k8s_프로필은_서비스_API_문서_라우트가_없다() {
        assertThat(load("application-k8s.yml").apiDocsRoutes()).isEmpty()
    }
}
