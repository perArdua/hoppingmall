package com.hoppingmall.user.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.hoppingmall.common.ApiResponse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springdoc.core.configuration.SpringDocConfiguration
import org.springdoc.core.configuration.SpringDocJacksonKotlinModuleConfiguration
import org.springdoc.core.configuration.SpringDocKotlinConfiguration
import org.springdoc.core.configuration.SpringDocPageableConfiguration
import org.springdoc.core.configuration.SpringDocSortConfiguration
import org.springdoc.core.configuration.SpringDocSpecPropertiesConfiguration
import org.springdoc.core.properties.SpringDocConfigProperties
import org.springdoc.core.properties.SwaggerUiConfigProperties
import org.springdoc.core.properties.SwaggerUiOAuthProperties
import org.springdoc.webmvc.core.configuration.MultipleOpenApiSupportConfiguration
import org.springdoc.webmvc.core.configuration.SpringDocWebMvcConfiguration
import org.springdoc.webmvc.ui.SwaggerConfig
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration
import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.boot.test.context.runner.WebApplicationContextRunner
import org.springframework.core.env.EnumerablePropertySource
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.StandardEnvironment
import org.springframework.core.io.ClassPathResource
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@DisplayName("운영 프로필의 API 명세 켜고 끔에 따른 기동")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class ApiDocsToggleContextTest {

    @RestController
    class DocsProbeController {
        @GetMapping("/docs-probe")
        fun probe(): ApiResponse<DocsProbe> = ApiResponse.success(DocsProbe("probe"))
    }

    data class DocsProbe(val probeValue: String)

    @ParameterizedTest
    @ValueSource(strings = ["docker", "k8s"])
    fun 명세를_끈_운영_프로필_값으로도_컨텍스트가_뜬다(profile: String) {
        contextRunner(profile, apiDocsEnabled = null).run { context ->
            assertThat(context).hasNotFailed()
            assertThat(context.environment.getProperty("springdoc.api-docs.enabled")).isEqualTo("false")

            val response = MockMvcBuilders.webAppContextSetup(context).build().get("/v3/api-docs").andReturn().response

            assertThat(response.status).isEqualTo(404)
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["docker", "k8s"])
    fun 명세를_켠_운영_프로필에서는_명세가_실제_응답_모양으로_나온다(profile: String) {
        contextRunner(profile, apiDocsEnabled = "true").run { context ->
            assertThat(context).hasNotFailed()

            val response = MockMvcBuilders.webAppContextSetup(context).build().get("/v3/api-docs").andReturn().response
            val docs = ObjectMapper().readTree(response.contentAsString)
            val responses = docs.path("paths").path("/docs-probe").path("get").path("responses")
            val envelopeName = responses.path("200").path("content").path("application/json").path("schema").path("\$ref").asText().substringAfterLast('/')
            val schemas = docs.path("components").path("schemas")
            val dataName = schemas.path(envelopeName).path("properties").path("data").path("\$ref").asText().substringAfterLast('/')

            assertThat(response.status).isEqualTo(200)
            assertThat(responses.path("200").path("content").has("*/*")).isFalse()
            assertThat(responses.path("default").path("content").path("application/json").path("schema").path("\$ref").asText())
                .isEqualTo("#/components/schemas/ErrorResponse")
            assertThat(schemas.path(dataName).path("properties").fieldNames().asSequence().toList()).containsExactly("probe_value")
        }
    }

    private fun contextRunner(profile: String, apiDocsEnabled: String?): WebApplicationContextRunner =
        WebApplicationContextRunner()
            .withConfiguration(
                AutoConfigurations.of(
                    JacksonAutoConfiguration::class.java,
                    HttpMessageConvertersAutoConfiguration::class.java,
                    WebMvcAutoConfiguration::class.java,
                    SpringDocConfiguration::class.java,
                    SpringDocConfigProperties::class.java,
                    SpringDocSpecPropertiesConfiguration::class.java,
                    SpringDocPageableConfiguration::class.java,
                    SpringDocSortConfiguration::class.java,
                    SpringDocKotlinConfiguration::class.java,
                    SpringDocJacksonKotlinModuleConfiguration::class.java,
                    SpringDocWebMvcConfiguration::class.java,
                    MultipleOpenApiSupportConfiguration::class.java,
                    SwaggerConfig::class.java,
                    SwaggerUiConfigProperties::class.java,
                    SwaggerUiOAuthProperties::class.java
                )
            )
            .withUserConfiguration(OpenApiConfig::class.java, DocsProbeController::class.java)
            .withPropertyValues(*profileProperties(profile, apiDocsEnabled))

    private fun profileProperties(profile: String, apiDocsEnabled: String?): Array<String> {
        val environment = StandardEnvironment()
        environment.propertySources.remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME)
        environment.propertySources.remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME)
        apiDocsEnabled?.let { environment.propertySources.addFirst(MapPropertySource("env", mapOf("API_DOCS_ENABLED" to it))) }
        listOf("application-$profile.yml", "application.yml").forEach { fileName ->
            YamlPropertySourceLoader().load(fileName, ClassPathResource(fileName)).forEach { environment.propertySources.addLast(it) }
        }
        return environment.propertySources
            .filterIsInstance<EnumerablePropertySource<*>>()
            .flatMap { it.propertyNames.toList() }
            .filter { it.startsWith("springdoc.") || it.startsWith("spring.jackson.") }
            .distinct()
            .map { "$it=${environment.getProperty(it)}" }
            .toTypedArray()
    }
}
